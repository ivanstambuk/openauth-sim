package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.CanonicalScenarios;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.ScenarioStores;
import io.openauth.sim.application.contract.hotp.HotpCanonicalScenarios;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.cli.HotpCli;
import io.openauth.sim.rest.support.PicocliHarness;
import io.openauth.sim.testing.JsonEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
            CanonicalScenario nativeScenario =
                    CanonicalScenarios.scenarioForDescriptor(nativeEnv, descriptor, HotpCanonicalScenarios::scenarios);
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
            CanonicalScenario restScenario =
                    CanonicalScenarios.scenarioForDescriptor(restEnv, descriptor, HotpCanonicalScenarios::scenarios);
            HotpEvaluationService restEval =
                    new HotpEvaluationService(new HotpEvaluationApplicationService(restEnv.store()));
            HotpReplayService restReplay = new HotpReplayService(new HotpReplayApplicationService(restEnv.store()));

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE -> {
                            HotpEvaluationApplicationService.EvaluationCommand.Inline inline =
                                    (HotpEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command();
                            yield toCanonical(restEval.evaluateInline(HotpContractRequests.evaluateInline(inline)));
                        }
                        case EVALUATE_STORED -> {
                            HotpEvaluationApplicationService.EvaluationCommand.Stored stored =
                                    (HotpEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command();
                            yield toCanonical(restEval.evaluateStored(HotpContractRequests.evaluateStored(stored)));
                        }
                        case REPLAY_STORED, FAILURE_STORED -> {
                            HotpReplayApplicationService.ReplayCommand.Stored stored =
                                    (HotpReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(HotpContractRequests.replayStored(stored)), stored.otp());
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            HotpReplayApplicationService.ReplayCommand.Inline inline =
                                    (HotpReplayApplicationService.ReplayCommand.Inline) restScenario.command();
                            yield toCanonical(
                                    restReplay.replay(HotpContractRequests.replayInline(inline)), inline.otp());
                        }
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            if (descriptor.kind() == CanonicalScenario.Kind.EVALUATE_INLINE
                    || descriptor.kind() == CanonicalScenario.Kind.EVALUATE_STORED) {
                Path cliDb = tempDir.resolve(descriptor.scenarioId() + ".db");
                ScenarioStores.seedFileStore(cliDb, Instant.EPOCH, HotpCanonicalScenarios::scenarios);
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
