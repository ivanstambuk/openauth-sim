package io.openauth.sim.rest.webauthn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.fido2.Fido2CanonicalScenarios;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.cli.Fido2Cli;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("crossFacadeContract")
final class Fido2CrossFacadeContractTest {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @TempDir
    Path tempDir;

    @Test
    void fido2CanonicalScenariosStayInParityAcrossFacades() throws Exception {
        List<CanonicalScenario> descriptors =
                Fido2CanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario nativeScenario = scenario(nativeEnv, descriptor.scenarioId());
            WebAuthnEvaluationApplicationService evaluation =
                    WebAuthnEvaluationApplicationService.usingDefaults(nativeEnv.store());
            WebAuthnReplayApplicationService replay = new WebAuthnReplayApplicationService(evaluation);

            CanonicalFacadeResult nativeResult = toCanonical(
                    replay.replay((WebAuthnReplayApplicationService.ReplayCommand) nativeScenario.command()));
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario restScenario = scenario(restEnv, descriptor.scenarioId());
            WebAuthnReplayApplicationService restReplayApp = new WebAuthnReplayApplicationService(
                    WebAuthnEvaluationApplicationService.usingDefaults(restEnv.store()));
            WebAuthnReplayService restReplayService = new WebAuthnReplayService(restReplayApp);

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case REPLAY_STORED, FAILURE_STORED -> {
                            WebAuthnReplayApplicationService.ReplayCommand.Stored stored =
                                    (WebAuthnReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            WebAuthnReplayRequest request = new WebAuthnReplayRequest(
                                    stored.credentialId(),
                                    null,
                                    stored.relyingPartyId(),
                                    stored.origin(),
                                    stored.expectedType(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    encode(stored.expectedChallenge()),
                                    encode(stored.clientDataJson()),
                                    encode(stored.authenticatorData()),
                                    encode(stored.signature()),
                                    null,
                                    false);
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
                            WebAuthnReplayRequest request = new WebAuthnReplayRequest(
                                    encode(inline.credentialId()),
                                    inline.credentialName(),
                                    inline.relyingPartyId(),
                                    inline.origin(),
                                    inline.expectedType(),
                                    encode(inline.publicKeyCose()),
                                    inline.signatureCounter(),
                                    inline.userVerificationRequired(),
                                    inline.algorithm().name(),
                                    encode(inline.expectedChallenge()),
                                    encode(inline.clientDataJson()),
                                    encode(inline.authenticatorData()),
                                    encode(inline.signature()),
                                    null,
                                    false);
                            yield toCanonical(restReplayService.replay(request));
                        }
                        default ->
                            throw new IllegalStateException("Unsupported FIDO2 scenario kind " + restScenario.kind());
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            if (restScenario.kind() == CanonicalScenario.Kind.REPLAY_STORED
                    || restScenario.kind() == CanonicalScenario.Kind.FAILURE_STORED) {
                Path dbPath = tempDir.resolve(descriptor.scenarioId() + ".db");
                seedCliStore(dbPath);
                CanonicalFacadeResult cliResult = executeCliReplay(dbPath, restScenario);
                assertEquals(expected, cliResult, descriptor.scenarioId() + " cli");
            }
        }
    }

    private static CanonicalScenario scenario(ScenarioEnvironment env, String id) {
        return Fido2CanonicalScenarios.scenarios(env).stream()
                .filter(scenario -> scenario.scenarioId().equals(id))
                .findFirst()
                .orElseThrow();
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
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(stderr, true, StandardCharsets.UTF_8);

        Fido2Cli cli = new Fido2Cli();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(out);
        cmd.setErr(err);

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
            encode(stored.expectedChallenge()),
            "--client-data",
            encode(stored.clientDataJson()),
            "--authenticator-data",
            encode(stored.authenticatorData()),
            "--signature",
            encode(stored.signature()),
            "--output-json"
        };

        cmd.execute(args);
        String json = stdout.toString(StandardCharsets.UTF_8);
        Map<String, Object> envelope = castMap(SimpleJson.parse(json));
        return toCanonicalCli(envelope);
    }

    private static CanonicalFacadeResult toCanonicalCli(Map<String, Object> envelope) {
        Map<String, Object> data = castMap(envelope.get("data"));
        Object matchValue = data != null ? data.get("match") : null;
        boolean match = matchValue instanceof Boolean booleanValue
                ? booleanValue
                : Boolean.parseBoolean(String.valueOf(matchValue));
        String rawReasonCode = String.valueOf(envelope.get("reasonCode"));
        boolean includeTrace = data != null && data.get("trace") != null;
        boolean telemetryPresent = envelope.get("telemetryId") != null;
        return new CanonicalFacadeResult(
                match, rawReasonCode, null, null, null, null, null, includeTrace, telemetryPresent);
    }

    private static String encode(byte[] value) {
        return value == null ? "" : URL_ENCODER.encodeToString(value);
    }

    private static void seedCliStore(Path dbPath) throws Exception {
        try (CredentialStore store = CredentialStoreFactory.openFileStore(dbPath)) {
            ScenarioEnvironment env = new ScenarioEnvironment(store, Clock.systemUTC());
            Fido2CanonicalScenarios.scenarios(env);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }
}
