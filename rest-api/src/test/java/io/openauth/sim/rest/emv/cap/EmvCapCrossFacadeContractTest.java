package io.openauth.sim.rest.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.emv.cap.EmvCapCanonicalScenarios;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.cli.EmvCli;
import io.openauth.sim.rest.support.CrossFacadeContractRunner;
import io.openauth.sim.rest.support.ObjectProviders;
import io.openauth.sim.rest.support.PicocliHarness;
import io.openauth.sim.testing.JsonEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("crossFacadeContract")
final class EmvCapCrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void emvCapCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        CrossFacadeContractRunner.CliContext cliContext =
                new CrossFacadeContractRunner.CliContext(tempDir, Instant.EPOCH, EmvCapCanonicalScenarios::scenarios);
        CrossFacadeContractRunner.assertParity(
                Instant.EPOCH,
                EmvCapCanonicalScenarios::scenarios,
                EmvCapCrossFacadeContractTest::executeNative,
                EmvCapCrossFacadeContractTest::executeRest,
                cliContext,
                this::executeCli,
                EmvCapCrossFacadeContractTest::assertCliParity);
    }

    private static CanonicalFacadeResult executeNative(ScenarioEnvironment env, CanonicalScenario scenario) {
        EmvCapEvaluationApplicationService eval = new EmvCapEvaluationApplicationService();
        EmvCapReplayApplicationService replay = new EmvCapReplayApplicationService(env.store(), eval);

        return switch (scenario.kind()) {
            case EVALUATE_INLINE ->
                toCanonical(eval.evaluate((EmvCapEvaluationApplicationService.EvaluationRequest) scenario.command()));
            case REPLAY_STORED, FAILURE_STORED -> {
                EmvCapReplayApplicationService.ReplayCommand.Stored stored =
                        (EmvCapReplayApplicationService.ReplayCommand.Stored) scenario.command();
                yield toCanonical(replay.replay(stored), stored.otp());
            }
            default -> throw new IllegalStateException("Unsupported EMV/CAP scenario kind " + scenario.kind());
        };
    }

    private static CanonicalFacadeResult executeRest(ScenarioEnvironment env, CanonicalScenario scenario) {
        EmvCapEvaluationApplicationService evalApp = new EmvCapEvaluationApplicationService();
        EmvCapEvaluationService eval = new EmvCapEvaluationService(evalApp, ObjectProviders.fixed(env.store()));
        EmvCapReplayService replay = new EmvCapReplayService(new EmvCapReplayApplicationService(env.store(), evalApp));

        return switch (scenario.kind()) {
            case EVALUATE_INLINE -> {
                EmvCapEvaluationApplicationService.EvaluationRequest inline =
                        (EmvCapEvaluationApplicationService.EvaluationRequest) scenario.command();
                EmvCapEvaluationRequest request = EmvCapContractRequests.evaluateInline(inline);
                yield toCanonical(eval.evaluate(request));
            }
            case REPLAY_STORED, FAILURE_STORED -> {
                EmvCapReplayApplicationService.ReplayCommand.Stored stored =
                        (EmvCapReplayApplicationService.ReplayCommand.Stored) scenario.command();
                EmvCapReplayRequest request = EmvCapContractRequests.replayStored(stored);
                yield toCanonical(replay.replay(request), stored.otp());
            }
            default -> throw new IllegalStateException("Unsupported EMV/CAP scenario kind " + scenario.kind());
        };
    }

    private Optional<CanonicalFacadeResult> executeCli(
            CrossFacadeContractRunner.CliContext ctx, CanonicalScenario descriptor, CanonicalScenario scenario)
            throws Exception {
        Path dbPath = ctx.seededFileStore(descriptor.scenarioId());
        CanonicalFacadeResult cliResult =
                switch (scenario.kind()) {
                    case EVALUATE_INLINE -> executeCliEvaluate(dbPath, scenario);
                    case REPLAY_STORED, FAILURE_STORED -> executeCliReplay(dbPath, scenario);
                    default -> throw new IllegalStateException("Unsupported EMV/CAP scenario kind " + scenario.kind());
                };
        return Optional.of(cliResult);
    }

    private static void assertCliParity(CanonicalFacadeResult expected, CanonicalFacadeResult actual, String message) {
        assertEquals(expected, actual, message);
    }

    private static CanonicalFacadeResult toCanonical(EmvCapEvaluationApplicationService.EvaluationResult result) {
        boolean success = result.telemetry().status() == EmvCapEvaluationApplicationService.TelemetryStatus.SUCCESS;
        boolean includeTrace = result.traceOptional().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                success ? result.otp() : null,
                null,
                null,
                null,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(EmvCapEvaluationResponse response) {
        boolean success = response.telemetry() != null
                && "success".equals(response.telemetry().status());
        String reasonCode = response.telemetry() != null ? response.telemetry().reasonCode() : "unexpected_error";
        return new CanonicalFacadeResult(
                success,
                reasonCode,
                response.otp(),
                null,
                null,
                null,
                null,
                response.trace() != null,
                response.telemetry() != null
                        && response.telemetry().fields() != null
                        && response.telemetry().fields().get("telemetryId") != null);
    }

    private static CanonicalFacadeResult toCanonical(
            EmvCapReplayApplicationService.ReplayResult result, String submittedOtp) {
        boolean success = result.match();
        boolean includeTrace = result.traceOptional().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                null,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(EmvCapReplayResponse response, String submittedOtp) {
        boolean success = "match".equals(response.reasonCode());
        EmvCapReplayMetadata meta = response.metadata();
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                null,
                null,
                response.trace() != null,
                meta != null && meta.telemetryId() != null);
    }

    private CanonicalFacadeResult executeCliEvaluate(Path dbPath, CanonicalScenario scenario) {
        EmvCapEvaluationApplicationService.EvaluationRequest inline =
                (EmvCapEvaluationApplicationService.EvaluationRequest) scenario.command();
        java.util.List<String> args = new java.util.ArrayList<>(List.of(
                "--database",
                dbPath.toString(),
                "cap",
                "evaluate",
                "--mode",
                inline.mode().name(),
                "--master-key",
                inline.masterKeyHex(),
                "--atc",
                inline.atcHex(),
                "--branch-factor",
                Integer.toString(inline.branchFactor()),
                "--height",
                Integer.toString(inline.height()),
                "--iv",
                inline.ivHex(),
                "--cdol1",
                inline.cdol1Hex(),
                "--issuer-proprietary-bitmap",
                inline.issuerProprietaryBitmapHex(),
                "--icc-template",
                inline.iccDataTemplateHex(),
                "--issuer-application-data",
                inline.issuerApplicationDataHex(),
                "--include-trace",
                "false",
                "--output-json"));
        if (!inline.customerInputs().challenge().isBlank()) {
            args.addAll(List.of("--challenge", inline.customerInputs().challenge()));
        }
        if (!inline.customerInputs().reference().isBlank()) {
            args.addAll(List.of("--reference", inline.customerInputs().reference()));
        }
        if (!inline.customerInputs().amount().isBlank()) {
            args.addAll(List.of("--amount", inline.customerInputs().amount()));
        }

        PicocliHarness.ExecutionResult result = PicocliHarness.execute(new EmvCli(), args.toArray(String[]::new));
        JsonEnvelope envelope = JsonEnvelope.parse(result.stdout());
        return toCanonicalCli(envelope);
    }

    private CanonicalFacadeResult executeCliReplay(Path dbPath, CanonicalScenario scenario) {
        EmvCapReplayApplicationService.ReplayCommand.Stored stored =
                (EmvCapReplayApplicationService.ReplayCommand.Stored) scenario.command();
        String[] args = new String[] {
            "--database",
            dbPath.toString(),
            "cap",
            "replay",
            "--credential-id",
            stored.credentialId(),
            "--mode",
            stored.mode().name(),
            "--otp",
            stored.otp(),
            "--include-trace",
            "false",
            "--output-json"
        };

        PicocliHarness.ExecutionResult result = PicocliHarness.execute(new EmvCli(), args);
        JsonEnvelope envelope = JsonEnvelope.parse(result.stdout());
        CanonicalFacadeResult base = toCanonicalCli(envelope);
        if (base.success() && base.otp() == null) {
            return new CanonicalFacadeResult(
                    true,
                    base.reasonCode(),
                    stored.otp(),
                    null,
                    null,
                    null,
                    null,
                    base.includeTrace(),
                    base.telemetryIdPresent());
        }
        return base;
    }

    private static CanonicalFacadeResult toCanonicalCli(JsonEnvelope envelope) {
        String reasonCode = envelope.reasonCode();
        boolean success = "match".equals(reasonCode) || "generated".equals(reasonCode);
        boolean telemetryPresent = envelope.telemetryIdPresent();
        String otp = envelope.dataString("otp");
        boolean includeTrace = envelope.tracePresent();
        return new CanonicalFacadeResult(
                success, reasonCode, success ? otp : null, null, null, null, null, includeTrace, telemetryPresent);
    }
}
