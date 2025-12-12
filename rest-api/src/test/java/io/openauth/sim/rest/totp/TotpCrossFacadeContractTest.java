package io.openauth.sim.rest.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.CanonicalScenarios;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.ScenarioStores;
import io.openauth.sim.application.contract.totp.TotpCanonicalScenarios;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.cli.TotpCli;
import io.openauth.sim.rest.support.PicocliHarness;
import io.openauth.sim.testing.JsonEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
            CanonicalScenario nativeScenario =
                    CanonicalScenarios.scenarioForDescriptor(nativeEnv, descriptor, TotpCanonicalScenarios::scenarios);
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
            CanonicalScenario restScenario =
                    CanonicalScenarios.scenarioForDescriptor(restEnv, descriptor, TotpCanonicalScenarios::scenarios);
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
                                    restEval.evaluateInline(TotpContractRequests.evaluateInline(inline)), epochSeconds);
                        }
                        case EVALUATE_STORED -> {
                            TotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                                    (TotpEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command();
                            yield toCanonical(
                                    restEval.evaluateStored(TotpContractRequests.evaluateStored(stored)), epochSeconds);
                        }
                        case REPLAY_STORED, FAILURE_STORED -> {
                            TotpReplayApplicationService.ReplayCommand.Stored stored =
                                    (TotpReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(TotpContractRequests.replayStored(stored)),
                                    epochSeconds,
                                    stored.otp());
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            TotpReplayApplicationService.ReplayCommand.Inline inline =
                                    (TotpReplayApplicationService.ReplayCommand.Inline) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(TotpContractRequests.replayInline(inline)),
                                    epochSeconds,
                                    inline.otp());
                        }
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            if (descriptor.kind() == CanonicalScenario.Kind.EVALUATE_INLINE
                    || descriptor.kind() == CanonicalScenario.Kind.EVALUATE_STORED) {
                Path cliDb = tempDir.resolve(descriptor.scenarioId() + ".db");
                ScenarioStores.seedFileStore(cliDb, Instant.EPOCH, TotpCanonicalScenarios::scenarios);
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

        PicocliHarness.ExecutionResult result = PicocliHarness.execute(new TotpCli(), args);
        JsonEnvelope envelope = JsonEnvelope.parse(result.stdout());
        return toCanonicalCli(envelope);
    }

    private static CanonicalFacadeResult toCanonicalCli(JsonEnvelope envelope) {
        boolean success = "success".equals(envelope.status());
        boolean telemetryPresent = envelope.telemetryIdPresent();
        String otp = envelope.dataString("otp");
        boolean includeTrace = envelope.tracePresent();
        return new CanonicalFacadeResult(
                success, envelope.reasonCode(), otp, null, null, null, null, includeTrace, telemetryPresent);
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
