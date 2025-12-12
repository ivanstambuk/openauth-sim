package io.openauth.sim.rest.webauthn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.CanonicalScenarios;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.ScenarioStores;
import io.openauth.sim.application.contract.fido2.Fido2CanonicalScenarios;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.cli.Fido2Cli;
import io.openauth.sim.rest.support.PicocliHarness;
import io.openauth.sim.testing.JsonEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("crossFacadeContract")
final class Fido2CrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void fido2CanonicalScenariosStayInParityAcrossFacades() throws Exception {
        List<CanonicalScenario> descriptors =
                Fido2CanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario nativeScenario =
                    CanonicalScenarios.scenarioForDescriptor(nativeEnv, descriptor, Fido2CanonicalScenarios::scenarios);
            WebAuthnEvaluationApplicationService evaluation =
                    WebAuthnEvaluationApplicationService.usingDefaults(nativeEnv.store());
            WebAuthnReplayApplicationService replay = new WebAuthnReplayApplicationService(evaluation);

            CanonicalFacadeResult nativeResult = toCanonical(
                    replay.replay((WebAuthnReplayApplicationService.ReplayCommand) nativeScenario.command()));
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario restScenario =
                    CanonicalScenarios.scenarioForDescriptor(restEnv, descriptor, Fido2CanonicalScenarios::scenarios);
            WebAuthnReplayApplicationService restReplayApp = new WebAuthnReplayApplicationService(
                    WebAuthnEvaluationApplicationService.usingDefaults(restEnv.store()));
            WebAuthnReplayService restReplayService = new WebAuthnReplayService(restReplayApp);

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case REPLAY_STORED, FAILURE_STORED -> {
                            WebAuthnReplayApplicationService.ReplayCommand.Stored stored =
                                    (WebAuthnReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            WebAuthnReplayRequest request = WebAuthnContractRequests.replayStored(stored);
                            try {
                                yield toCanonical(restReplayService.replay(request));
                            } catch (WebAuthnReplayValidationException ex) {
                                yield new CanonicalFacadeResult(
                                        false, ex.reasonCode(), null, null, null, null, null, ex.trace() != null, true);
                            }
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            WebAuthnReplayApplicationService.ReplayCommand.Inline inline =
                                    (WebAuthnReplayApplicationService.ReplayCommand.Inline) restScenario.command();
                            WebAuthnReplayRequest request = WebAuthnContractRequests.replayInline(inline);
                            yield toCanonical(restReplayService.replay(request));
                        }
                        default ->
                            throw new IllegalStateException("Unsupported FIDO2 scenario kind " + restScenario.kind());
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            if (restScenario.kind() == CanonicalScenario.Kind.REPLAY_STORED
                    || restScenario.kind() == CanonicalScenario.Kind.FAILURE_STORED) {
                Path dbPath = tempDir.resolve(descriptor.scenarioId() + ".db");
                ScenarioStores.seedFileStore(dbPath, Instant.EPOCH, Fido2CanonicalScenarios::scenarios);
                CanonicalFacadeResult cliResult = executeCliReplay(dbPath, restScenario);
                assertEquals(expected, cliResult, descriptor.scenarioId() + " cli");
            }
        }
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
