package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.hotp.HotpCanonicalScenarios;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.cli.HotpCli;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("crossFacadeContract")
class HotpCrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void hotpCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        List<CanonicalScenario> descriptors =
                HotpCanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario nativeScenario = HotpCanonicalScenarios.scenarios(nativeEnv).stream()
                    .filter(s -> s.scenarioId().equals(descriptor.scenarioId()))
                    .findFirst()
                    .orElseThrow();
            HotpEvaluationApplicationService nativeEval = new HotpEvaluationApplicationService(nativeEnv.store());
            HotpReplayApplicationService nativeReplay = new HotpReplayApplicationService(nativeEnv.store());

            CanonicalFacadeResult nativeResult =
                    switch (nativeScenario.kind()) {
                        case EVALUATE_INLINE, EVALUATE_STORED ->
                            toCanonical(nativeEval.evaluate(
                                    (HotpEvaluationApplicationService.EvaluationCommand) nativeScenario.command()));
                        case REPLAY_INLINE, REPLAY_STORED, FAILURE_INLINE, FAILURE_STORED -> {
                            HotpReplayApplicationService.ReplayCommand replayCommand =
                                    (HotpReplayApplicationService.ReplayCommand) nativeScenario.command();
                            yield toCanonical(nativeReplay.replay(replayCommand), replayCommand.otp());
                        }
                    };
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario restScenario = HotpCanonicalScenarios.scenarios(restEnv).stream()
                    .filter(s -> s.scenarioId().equals(descriptor.scenarioId()))
                    .findFirst()
                    .orElseThrow();
            HotpEvaluationService restEval =
                    new HotpEvaluationService(new HotpEvaluationApplicationService(restEnv.store()));
            HotpReplayService restReplay = new HotpReplayService(new HotpReplayApplicationService(restEnv.store()));

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE -> {
                            HotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                                    (HotpEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command();
                            yield toCanonical(restEval.evaluateInline(new HotpInlineEvaluationRequest(
                                    inline.sharedSecretHex(),
                                    null,
                                    inline.algorithm().name(),
                                    inline.digits(),
                                    null,
                                    inline.counter(),
                                    inline.metadata(),
                                    false)));
                        }
                        case EVALUATE_STORED -> {
                            HotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                                    (HotpEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command();
                            yield toCanonical(restEval.evaluateStored(
                                    new HotpStoredEvaluationRequest(stored.credentialId(), null, false)));
                        }
                        case REPLAY_STORED, FAILURE_STORED -> {
                            HotpReplayApplicationService.ReplayCommand.Stored stored =
                                    (HotpReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(new HotpReplayRequest(
                                            stored.credentialId(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            stored.otp(),
                                            null,
                                            false)),
                                    stored.otp());
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            HotpReplayApplicationService.ReplayCommand.Inline inline =
                                    (HotpReplayApplicationService.ReplayCommand.Inline) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(new HotpReplayRequest(
                                            null,
                                            inline.sharedSecretHex(),
                                            null,
                                            inline.algorithm().name(),
                                            inline.digits(),
                                            inline.counter(),
                                            inline.otp(),
                                            null,
                                            false)),
                                    inline.otp());
                        }
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            if (descriptor.kind() == CanonicalScenario.Kind.EVALUATE_INLINE
                    || descriptor.kind() == CanonicalScenario.Kind.EVALUATE_STORED) {
                Path cliDb = tempDir.resolve(descriptor.scenarioId() + ".db");
                seedCliStore(cliDb);
                CanonicalFacadeResult cliResult = executeCliEvaluate(cliDb, restScenario);
                assertEquals(
                        expected.reasonCode(), cliResult.reasonCode(), descriptor.scenarioId() + " cli reasonCode");
                assertEquals(expected.otp(), cliResult.otp(), descriptor.scenarioId() + " cli otp");
                assertEquals(
                        expected.previousCounter(),
                        cliResult.previousCounter(),
                        descriptor.scenarioId() + " cli prevCounter");
                assertEquals(
                        expected.nextCounter(), cliResult.nextCounter(), descriptor.scenarioId() + " cli nextCounter");
            }
        }
    }

    private static CanonicalFacadeResult toCanonical(HotpEvaluationApplicationService.EvaluationResult result) {
        boolean success = result.telemetry().status() == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS;
        boolean includeTrace = result.verboseTrace().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                result.otp(),
                result.previousCounter(),
                result.nextCounter(),
                null,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(
            HotpReplayApplicationService.ReplayResult result, String submittedOtp) {
        boolean success = result.telemetry().status() == HotpReplayApplicationService.TelemetryStatus.SUCCESS;
        boolean includeTrace = result.verboseTrace().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                success ? submittedOtp : null,
                result.previousCounter(),
                result.nextCounter(),
                null,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(HotpEvaluationResponse response) {
        boolean success = "generated".equals(response.status());
        HotpEvaluationMetadata meta = response.metadata();
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                response.otp(),
                meta != null ? meta.previousCounter() : null,
                meta != null ? meta.nextCounter() : null,
                null,
                null,
                response.trace() != null,
                meta != null && meta.telemetryId() != null);
    }

    private static CanonicalFacadeResult toCanonical(HotpReplayResponse response, String submittedOtp) {
        boolean success = "match".equals(response.status());
        HotpReplayMetadata meta = response.metadata();
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                success ? submittedOtp : null,
                meta != null ? meta.previousCounter() : null,
                meta != null ? meta.nextCounter() : null,
                null,
                null,
                response.trace() != null,
                meta != null && meta.telemetryId() != null);
    }

    private CanonicalFacadeResult executeCliEvaluate(Path dbPath, CanonicalScenario scenario) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(stderr, true, StandardCharsets.UTF_8);

        HotpCli cli = new HotpCli();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(out);
        cmd.setErr(err);

        String[] args;
        if (scenario.kind() == CanonicalScenario.Kind.EVALUATE_INLINE) {
            HotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                    (HotpEvaluationApplicationService.EvaluationCommand.Inline) scenario.command();
            args = new String[] {
                "--database",
                dbPath.toString(),
                "evaluate",
                "--secret",
                inline.sharedSecretHex(),
                "--digits",
                Integer.toString(inline.digits()),
                "--counter",
                inline.counter().toString(),
                "--algorithm",
                inline.algorithm().name(),
                "--output-json"
            };
        } else {
            HotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                    (HotpEvaluationApplicationService.EvaluationCommand.Stored) scenario.command();
            args = new String[] {
                "--database", dbPath.toString(), "evaluate", "--credential-id", stored.credentialId(), "--output-json"
            };
        }

        cmd.execute(args);
        String json = stdout.toString(StandardCharsets.UTF_8);
        Map<String, Object> payload = castMap(SimpleJson.parse(json));
        return toCanonicalCli(payload);
    }

    private static CanonicalFacadeResult toCanonicalCli(Map<String, Object> envelope) {
        String status = String.valueOf(envelope.get("status"));
        String reasonCode = String.valueOf(envelope.get("reasonCode"));
        boolean success = "success".equals(status);
        boolean telemetryPresent = envelope.get("telemetryId") != null;
        Map<String, Object> data = castMap(envelope.get("data"));
        String otp = data != null ? (String) data.get("otp") : null;
        Long prev = toLong(data != null ? data.get("previousCounter") : null);
        Long next = toLong(data != null ? data.get("nextCounter") : null);
        boolean includeTrace = data != null && data.get("trace") != null;
        return new CanonicalFacadeResult(
                success, reasonCode, otp, prev, next, null, null, includeTrace, telemetryPresent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static void seedCliStore(Path dbPath) throws Exception {
        try (CredentialStore store = CredentialStoreFactory.openFileStore(dbPath)) {
            ScenarioEnvironment env = new ScenarioEnvironment(store, Clock.systemUTC());
            HotpCanonicalScenarios.scenarios(env);
        }
    }
}
