package io.openauth.sim.rest.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.totp.TotpCanonicalScenarios;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.cli.TotpCli;
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
class TotpCrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void totpCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        List<CanonicalScenario> descriptors =
                TotpCanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario nativeScenario = TotpCanonicalScenarios.scenarios(nativeEnv).stream()
                    .filter(s -> s.scenarioId().equals(descriptor.scenarioId()))
                    .findFirst()
                    .orElseThrow();
            TotpEvaluationApplicationService nativeEval =
                    new TotpEvaluationApplicationService(nativeEnv.store(), nativeEnv.clock());
            TotpReplayApplicationService nativeReplay = new TotpReplayApplicationService(nativeEval);

            Long epochSeconds = epochSeconds(nativeScenario);
            CanonicalFacadeResult nativeResult =
                    switch (nativeScenario.kind()) {
                        case EVALUATE_INLINE, EVALUATE_STORED ->
                            toCanonical(
                                    nativeEval.evaluate((TotpEvaluationApplicationService.EvaluationCommand)
                                            nativeScenario.command()),
                                    epochSeconds);
                        case REPLAY_INLINE, REPLAY_STORED, FAILURE_INLINE, FAILURE_STORED -> {
                            TotpReplayApplicationService.ReplayCommand replayCommand =
                                    (TotpReplayApplicationService.ReplayCommand) nativeScenario.command();
                            yield toCanonical(nativeReplay.replay(replayCommand), epochSeconds, replayCommand.otp());
                        }
                    };
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario restScenario = TotpCanonicalScenarios.scenarios(restEnv).stream()
                    .filter(s -> s.scenarioId().equals(descriptor.scenarioId()))
                    .findFirst()
                    .orElseThrow();
            TotpEvaluationApplicationService restApp =
                    new TotpEvaluationApplicationService(restEnv.store(), restEnv.clock());
            TotpEvaluationService restEval = new TotpEvaluationService(restApp);
            TotpReplayService restReplay = new TotpReplayService(new TotpReplayApplicationService(restApp));

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE -> {
                            TotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                                    (TotpEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command();
                            yield toCanonical(
                                    restEval.evaluateInline(new TotpInlineEvaluationRequest(
                                            inline.sharedSecretHex(),
                                            null,
                                            inline.algorithm().name(),
                                            inline.digits(),
                                            inline.stepDuration().toSeconds(),
                                            null,
                                            inline.evaluationInstant().getEpochSecond(),
                                            null,
                                            inline.otp(),
                                            null,
                                            false)),
                                    epochSeconds);
                        }
                        case EVALUATE_STORED -> {
                            TotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                                    (TotpEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command();
                            yield toCanonical(
                                    restEval.evaluateStored(new TotpStoredEvaluationRequest(
                                            stored.credentialId(),
                                            stored.otp(),
                                            stored.evaluationInstant().getEpochSecond(),
                                            null,
                                            null,
                                            false)),
                                    epochSeconds);
                        }
                        case REPLAY_STORED, FAILURE_STORED -> {
                            TotpReplayApplicationService.ReplayCommand.Stored stored =
                                    (TotpReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(new TotpReplayRequest(
                                            stored.credentialId(),
                                            stored.otp(),
                                            stored.evaluationInstant().getEpochSecond(),
                                            null,
                                            stored.driftWindow().backwardSteps(),
                                            stored.driftWindow().forwardSteps(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            false)),
                                    epochSeconds,
                                    stored.otp());
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            TotpReplayApplicationService.ReplayCommand.Inline inline =
                                    (TotpReplayApplicationService.ReplayCommand.Inline) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(new TotpReplayRequest(
                                            null,
                                            inline.otp(),
                                            inline.evaluationInstant().getEpochSecond(),
                                            null,
                                            inline.driftWindow().backwardSteps(),
                                            inline.driftWindow().forwardSteps(),
                                            inline.sharedSecretHex(),
                                            null,
                                            inline.algorithm().name(),
                                            inline.digits(),
                                            inline.stepDuration().toSeconds(),
                                            false)),
                                    epochSeconds,
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
            }
        }
    }

    private static CanonicalFacadeResult toCanonical(
            TotpEvaluationApplicationService.EvaluationResult result, Long epochSeconds) {
        boolean success = result.telemetry().status() == TotpEvaluationApplicationService.TelemetryStatus.SUCCESS;
        boolean includeTrace = result.verboseTrace().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                result.otp(),
                null,
                null,
                epochSeconds,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(
            TotpReplayApplicationService.ReplayResult result, Long epochSeconds, String submittedOtp) {
        boolean success = result.telemetry().status() == TotpEvaluationApplicationService.TelemetryStatus.SUCCESS;
        boolean includeTrace = result.verboseTrace().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                epochSeconds,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(TotpEvaluationResponse response, Long epochSeconds) {
        boolean success = "generated".equals(response.status()) || "validated".equals(response.status());
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                response.otp(),
                null,
                null,
                epochSeconds,
                null,
                response.trace() != null,
                response.metadata() != null && response.metadata().telemetryId() != null);
    }

    private static CanonicalFacadeResult toCanonical(
            TotpReplayResponse response, Long epochSeconds, String submittedOtp) {
        boolean success = "match".equals(response.status());
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                epochSeconds,
                null,
                response.trace() != null,
                response.metadata() != null && response.metadata().telemetryId() != null);
    }

    private CanonicalFacadeResult executeCliEvaluate(Path dbPath, CanonicalScenario scenario) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(stderr, true, StandardCharsets.UTF_8);

        TotpCli cli = new TotpCli();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(out);
        cmd.setErr(err);

        String[] args;
        if (scenario.kind() == CanonicalScenario.Kind.EVALUATE_INLINE) {
            TotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                    (TotpEvaluationApplicationService.EvaluationCommand.Inline) scenario.command();
            args = new String[] {
                "--database",
                dbPath.toString(),
                "evaluate",
                "--secret",
                inline.sharedSecretHex(),
                "--algorithm",
                inline.algorithm().name(),
                "--digits",
                Integer.toString(inline.digits()),
                "--step-seconds",
                Long.toString(inline.stepDuration().toSeconds()),
                "--timestamp",
                Long.toString(inline.evaluationInstant().getEpochSecond()),
                "--output-json"
            };
        } else {
            TotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                    (TotpEvaluationApplicationService.EvaluationCommand.Stored) scenario.command();
            args = new String[] {
                "--database",
                dbPath.toString(),
                "evaluate",
                "--credential-id",
                stored.credentialId(),
                "--timestamp",
                Long.toString(stored.evaluationInstant().getEpochSecond()),
                "--output-json"
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
        boolean includeTrace = data != null && data.get("trace") != null;
        return new CanonicalFacadeResult(
                success, reasonCode, otp, null, null, null, null, includeTrace, telemetryPresent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }

    private static void seedCliStore(Path dbPath) throws Exception {
        try (CredentialStore store = CredentialStoreFactory.openFileStore(dbPath)) {
            ScenarioEnvironment env = new ScenarioEnvironment(store, Clock.systemUTC());
            TotpCanonicalScenarios.scenarios(env);
        }
    }

    private static Long epochSeconds(CanonicalScenario scenario) {
        return switch (scenario.kind()) {
            case EVALUATE_INLINE ->
                ((TotpEvaluationApplicationService.EvaluationCommand.Inline) scenario.command())
                        .evaluationInstant()
                        .getEpochSecond();
            case EVALUATE_STORED ->
                ((TotpEvaluationApplicationService.EvaluationCommand.Stored) scenario.command())
                        .evaluationInstant()
                        .getEpochSecond();
            case REPLAY_INLINE, FAILURE_INLINE ->
                ((TotpReplayApplicationService.ReplayCommand.Inline) scenario.command())
                        .evaluationInstant()
                        .getEpochSecond();
            case REPLAY_STORED, FAILURE_STORED ->
                ((TotpReplayApplicationService.ReplayCommand.Stored) scenario.command())
                        .evaluationInstant()
                        .getEpochSecond();
        };
    }
}
