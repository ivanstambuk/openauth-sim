package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.hotp.HotpCanonicalScenarios;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.cli.HotpCli;
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
class HotpCrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void hotpCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        CrossFacadeContractRunner.CliContext cliContext =
                new CrossFacadeContractRunner.CliContext(tempDir, Instant.EPOCH, HotpCanonicalScenarios::scenarios);
        CrossFacadeContractRunner.assertParity(
                Instant.EPOCH,
                HotpCanonicalScenarios::scenarios,
                HotpCrossFacadeContractTest::executeNative,
                HotpCrossFacadeContractTest::executeRest,
                cliContext,
                this::executeCli,
                HotpCrossFacadeContractTest::assertCliParity);
    }

    private static CanonicalFacadeResult executeNative(ScenarioEnvironment env, CanonicalScenario scenario) {
        HotpEvaluationApplicationService eval = new HotpEvaluationApplicationService(env.store());
        HotpReplayApplicationService replay = new HotpReplayApplicationService(env.store());

        return switch (scenario.kind()) {
            case EVALUATE_INLINE, EVALUATE_STORED ->
                toCanonical(eval.evaluate((HotpEvaluationApplicationService.EvaluationCommand) scenario.command()));
            case REPLAY_INLINE, REPLAY_STORED, FAILURE_INLINE, FAILURE_STORED -> {
                HotpReplayApplicationService.ReplayCommand replayCommand =
                        (HotpReplayApplicationService.ReplayCommand) scenario.command();
                yield toCanonical(replay.replay(replayCommand), replayCommand.otp());
            }
        };
    }

    private static CanonicalFacadeResult executeRest(ScenarioEnvironment env, CanonicalScenario scenario) {
        HotpEvaluationService eval = new HotpEvaluationService(new HotpEvaluationApplicationService(env.store()));
        HotpReplayService replay = new HotpReplayService(new HotpReplayApplicationService(env.store()));

        return switch (scenario.kind()) {
            case EVALUATE_INLINE -> {
                HotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                        (HotpEvaluationApplicationService.EvaluationCommand.Inline) scenario.command();
                yield toCanonical(eval.evaluateInline(HotpContractRequests.evaluateInline(inline)));
            }
            case EVALUATE_STORED -> {
                HotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                        (HotpEvaluationApplicationService.EvaluationCommand.Stored) scenario.command();
                yield toCanonical(eval.evaluateStored(HotpContractRequests.evaluateStored(stored)));
            }
            case REPLAY_STORED, FAILURE_STORED -> {
                HotpReplayApplicationService.ReplayCommand.Stored stored =
                        (HotpReplayApplicationService.ReplayCommand.Stored) scenario.command();
                yield toCanonical(replay.replay(HotpContractRequests.replayStored(stored)), stored.otp());
            }
            case REPLAY_INLINE, FAILURE_INLINE -> {
                HotpReplayApplicationService.ReplayCommand.Inline inline =
                        (HotpReplayApplicationService.ReplayCommand.Inline) scenario.command();
                yield toCanonical(replay.replay(HotpContractRequests.replayInline(inline)), inline.otp());
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
        assertEquals(expected.previousCounter(), actual.previousCounter(), message + " prevCounter");
        assertEquals(expected.nextCounter(), actual.nextCounter(), message + " nextCounter");
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

        PicocliHarness.ExecutionResult result = PicocliHarness.execute(new HotpCli(), args);
        JsonEnvelope envelope = JsonEnvelope.parse(result.stdout());
        return toCanonicalCli(envelope);
    }

    private static CanonicalFacadeResult toCanonicalCli(JsonEnvelope envelope) {
        boolean success = "success".equals(envelope.status());
        boolean telemetryPresent = envelope.telemetryIdPresent();
        String otp = envelope.dataString("otp");
        Long prev = envelope.dataLong("previousCounter");
        Long next = envelope.dataLong("nextCounter");
        boolean includeTrace = envelope.tracePresent();
        return new CanonicalFacadeResult(
                success, envelope.reasonCode(), otp, prev, next, null, null, includeTrace, telemetryPresent);
    }
}
