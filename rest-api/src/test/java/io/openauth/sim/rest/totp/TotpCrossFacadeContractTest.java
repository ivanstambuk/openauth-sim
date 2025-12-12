package io.openauth.sim.rest.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.totp.TotpCanonicalScenarios;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.cli.TotpCli;
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
class TotpCrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void totpCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        CrossFacadeContractRunner.CliContext cliContext =
                new CrossFacadeContractRunner.CliContext(tempDir, Instant.EPOCH, TotpCanonicalScenarios::scenarios);
        CrossFacadeContractRunner.assertParity(
                Instant.EPOCH,
                TotpCanonicalScenarios::scenarios,
                TotpCrossFacadeContractTest::executeNative,
                TotpCrossFacadeContractTest::executeRest,
                cliContext,
                this::executeCli,
                TotpCrossFacadeContractTest::assertCliParity);
    }

    private static CanonicalFacadeResult executeNative(ScenarioEnvironment env, CanonicalScenario scenario) {
        TotpEvaluationApplicationService eval = new TotpEvaluationApplicationService(env.store(), env.clock());
        TotpReplayApplicationService replay = new TotpReplayApplicationService(eval);
        Long epochSeconds = epochSeconds(scenario);

        return switch (scenario.kind()) {
            case EVALUATE_INLINE, EVALUATE_STORED ->
                toCanonical(
                        eval.evaluate((TotpEvaluationApplicationService.EvaluationCommand) scenario.command()),
                        epochSeconds);
            case REPLAY_INLINE, REPLAY_STORED, FAILURE_INLINE, FAILURE_STORED -> {
                TotpReplayApplicationService.ReplayCommand replayCommand =
                        (TotpReplayApplicationService.ReplayCommand) scenario.command();
                yield toCanonical(replay.replay(replayCommand), epochSeconds, replayCommand.otp());
            }
        };
    }

    private static CanonicalFacadeResult executeRest(ScenarioEnvironment env, CanonicalScenario scenario) {
        TotpEvaluationApplicationService app = new TotpEvaluationApplicationService(env.store(), env.clock());
        TotpEvaluationService eval = new TotpEvaluationService(app);
        TotpReplayService replay = new TotpReplayService(new TotpReplayApplicationService(app));
        Long epochSeconds = epochSeconds(scenario);

        return switch (scenario.kind()) {
            case EVALUATE_INLINE -> {
                TotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                        (TotpEvaluationApplicationService.EvaluationCommand.Inline) scenario.command();
                yield toCanonical(eval.evaluateInline(TotpContractRequests.evaluateInline(inline)), epochSeconds);
            }
            case EVALUATE_STORED -> {
                TotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                        (TotpEvaluationApplicationService.EvaluationCommand.Stored) scenario.command();
                yield toCanonical(eval.evaluateStored(TotpContractRequests.evaluateStored(stored)), epochSeconds);
            }
            case REPLAY_STORED, FAILURE_STORED -> {
                TotpReplayApplicationService.ReplayCommand.Stored stored =
                        (TotpReplayApplicationService.ReplayCommand.Stored) scenario.command();
                yield toCanonical(replay.replay(TotpContractRequests.replayStored(stored)), epochSeconds, stored.otp());
            }
            case REPLAY_INLINE, FAILURE_INLINE -> {
                TotpReplayApplicationService.ReplayCommand.Inline inline =
                        (TotpReplayApplicationService.ReplayCommand.Inline) scenario.command();
                yield toCanonical(replay.replay(TotpContractRequests.replayInline(inline)), epochSeconds, inline.otp());
            }
        };
    }

    private Optional<CanonicalFacadeResult> executeCli(
            CrossFacadeContractRunner.CliContext ctx, CanonicalScenario descriptor, CanonicalScenario scenario)
            throws Exception {
        if (scenario.kind() != CanonicalScenario.Kind.EVALUATE_INLINE
                && scenario.kind() != CanonicalScenario.Kind.EVALUATE_STORED) {
            return Optional.empty();
        }

        Path dbPath = ctx.seededFileStore(descriptor.scenarioId());
        return Optional.of(executeCliEvaluate(dbPath, scenario));
    }

    private static void assertCliParity(CanonicalFacadeResult expected, CanonicalFacadeResult actual, String message) {
        assertEquals(expected.reasonCode(), actual.reasonCode(), message + " reasonCode");
        assertEquals(expected.otp(), actual.otp(), message + " otp");
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
