package io.openauth.sim.rest.webauthn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.fido2.Fido2CanonicalScenarios;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.cli.Fido2Cli;
import io.openauth.sim.rest.support.CrossFacadeContractRunner;
import io.openauth.sim.rest.support.PicocliHarness;
import io.openauth.sim.testing.JsonEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("crossFacadeContract")
final class Fido2CrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void fido2CanonicalScenariosStayInParityAcrossFacades() throws Exception {
        CrossFacadeContractRunner.CliContext cliContext =
                new CrossFacadeContractRunner.CliContext(tempDir, Instant.EPOCH, Fido2CanonicalScenarios::scenarios);
        CrossFacadeContractRunner.assertParity(
                Instant.EPOCH,
                Fido2CanonicalScenarios::scenarios,
                Fido2CrossFacadeContractTest::executeNative,
                Fido2CrossFacadeContractTest::executeRest,
                cliContext,
                this::executeCli,
                Fido2CrossFacadeContractTest::assertCliParity);
    }

    private static CanonicalFacadeResult executeNative(ScenarioEnvironment env, CanonicalScenario scenario) {
        WebAuthnEvaluationApplicationService evaluation =
                WebAuthnEvaluationApplicationService.usingDefaults(env.store());
        WebAuthnReplayApplicationService replay = new WebAuthnReplayApplicationService(evaluation);
        return toCanonical(replay.replay((WebAuthnReplayApplicationService.ReplayCommand) scenario.command()));
    }

    private static CanonicalFacadeResult executeRest(ScenarioEnvironment env, CanonicalScenario scenario) {
        WebAuthnReplayApplicationService replayApp =
                new WebAuthnReplayApplicationService(WebAuthnEvaluationApplicationService.usingDefaults(env.store()));
        WebAuthnReplayService replayService = new WebAuthnReplayService(replayApp);

        return switch (scenario.kind()) {
            case REPLAY_STORED, FAILURE_STORED -> {
                WebAuthnReplayApplicationService.ReplayCommand.Stored stored =
                        (WebAuthnReplayApplicationService.ReplayCommand.Stored) scenario.command();
                WebAuthnReplayRequest request = WebAuthnContractRequests.replayStored(stored);
                try {
                    yield toCanonical(replayService.replay(request));
                } catch (WebAuthnReplayValidationException ex) {
                    yield new CanonicalFacadeResult(
                            false, ex.reasonCode(), null, null, null, null, null, ex.trace() != null, true);
                }
            }
            case REPLAY_INLINE, FAILURE_INLINE -> {
                WebAuthnReplayApplicationService.ReplayCommand.Inline inline =
                        (WebAuthnReplayApplicationService.ReplayCommand.Inline) scenario.command();
                WebAuthnReplayRequest request = WebAuthnContractRequests.replayInline(inline);
                yield toCanonical(replayService.replay(request));
            }
            default -> throw new IllegalStateException("Unsupported FIDO2 scenario kind " + scenario.kind());
        };
    }

    private Optional<CanonicalFacadeResult> executeCli(
            CrossFacadeContractRunner.CliContext ctx, CanonicalScenario descriptor, CanonicalScenario scenario)
            throws Exception {
        if (scenario.kind() != CanonicalScenario.Kind.REPLAY_STORED
                && scenario.kind() != CanonicalScenario.Kind.FAILURE_STORED) {
            return Optional.empty();
        }

        Path dbPath = ctx.seededFileStore(descriptor.scenarioId());
        return Optional.of(executeCliReplay(dbPath, scenario));
    }

    private static void assertCliParity(CanonicalFacadeResult expected, CanonicalFacadeResult actual, String message) {
        assertEquals(expected, actual, message);
    }

    private static CanonicalFacadeResult toCanonical(WebAuthnReplayApplicationService.ReplayResult result) {
        return new CanonicalFacadeResult(
                result.match(),
                result.telemetry().reasonCode(),
                null,
                null,
                null,
                null,
                null,
                result.verboseTrace().isPresent(),
                true);
    }

    private static CanonicalFacadeResult toCanonical(WebAuthnReplayResponse response) {
        boolean success = response.match();
        WebAuthnReplayMetadata meta = response.metadata();
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                null,
                null,
                null,
                null,
                null,
                response.trace() != null,
                meta != null && meta.telemetryId() != null);
    }

    private CanonicalFacadeResult executeCliReplay(Path dbPath, CanonicalScenario scenario) {
        WebAuthnReplayApplicationService.ReplayCommand.Stored stored =
                (WebAuthnReplayApplicationService.ReplayCommand.Stored) scenario.command();
        String[] args = new String[] {
            "--database",
            dbPath.toString(),
            "replay",
            "--credential-id",
            stored.credentialId(),
            "--relying-party-id",
            stored.relyingPartyId(),
            "--origin",
            stored.origin(),
            "--type",
            stored.expectedType(),
            "--expected-challenge",
            WebAuthnContractRequests.encode(stored.expectedChallenge()),
            "--client-data",
            WebAuthnContractRequests.encode(stored.clientDataJson()),
            "--authenticator-data",
            WebAuthnContractRequests.encode(stored.authenticatorData()),
            "--signature",
            WebAuthnContractRequests.encode(stored.signature()),
            "--output-json"
        };

        PicocliHarness.ExecutionResult result = PicocliHarness.execute(new Fido2Cli(), args);
        JsonEnvelope envelope = JsonEnvelope.parse(result.stdout());
        return toCanonicalCli(envelope);
    }

    private static CanonicalFacadeResult toCanonicalCli(JsonEnvelope envelope) {
        boolean match = envelope.dataBoolean("match");
        boolean includeTrace = envelope.tracePresent();
        boolean telemetryPresent = envelope.telemetryIdPresent();
        return new CanonicalFacadeResult(
                match, envelope.reasonCode(), null, null, null, null, null, includeTrace, telemetryPresent);
    }
}
