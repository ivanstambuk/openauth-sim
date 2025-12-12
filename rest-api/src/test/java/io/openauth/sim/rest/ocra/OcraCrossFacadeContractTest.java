package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.ocra.OcraCanonicalScenarios;
import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.rest.support.CrossFacadeContractRunner;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossFacadeContract")
class OcraCrossFacadeContractTest {

    @Test
    void ocraCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        CrossFacadeContractRunner.assertParity(
                Instant.EPOCH,
                OcraCanonicalScenarios::scenarios,
                OcraCrossFacadeContractTest::executeNative,
                OcraCrossFacadeContractTest::executeRest);
    }

    private static CanonicalFacadeResult executeNative(ScenarioEnvironment env, CanonicalScenario scenario) {
        OcraEvaluationApplicationService eval =
                new OcraEvaluationApplicationService(Clock.systemUTC(), OcraCredentialResolvers.forStore(env.store()));
        OcraVerificationApplicationService verify = new OcraVerificationApplicationService(
                OcraCredentialResolvers.forVerificationStore(env.store()), env.store());

        return switch (scenario.kind()) {
            case EVALUATE_INLINE, EVALUATE_STORED ->
                toCanonical(eval.evaluate((OcraEvaluationApplicationService.EvaluationCommand) scenario.command()));
            case REPLAY_INLINE, REPLAY_STORED, FAILURE_INLINE, FAILURE_STORED ->
                toCanonical(verify.verify((OcraVerificationApplicationService.VerificationCommand) scenario.command()));
        };
    }

    private static CanonicalFacadeResult executeRest(ScenarioEnvironment env, CanonicalScenario scenario) {
        OcraEvaluationService eval = new OcraEvaluationService(
                new OcraEvaluationApplicationService(Clock.systemUTC(), OcraCredentialResolvers.forStore(env.store())));
        OcraVerificationService verify = new OcraVerificationService(new OcraVerificationApplicationService(
                OcraCredentialResolvers.forVerificationStore(env.store()), env.store()));

        return switch (scenario.kind()) {
            case EVALUATE_INLINE -> {
                OcraEvaluationApplicationService.EvaluationCommand.Inline inline =
                        (OcraEvaluationApplicationService.EvaluationCommand.Inline) scenario.command();
                yield toCanonical(eval.evaluate(OcraContractRequests.evaluateInline(inline)));
            }
            case EVALUATE_STORED -> {
                OcraEvaluationApplicationService.EvaluationCommand.Stored stored =
                        (OcraEvaluationApplicationService.EvaluationCommand.Stored) scenario.command();
                yield toCanonical(eval.evaluate(OcraContractRequests.evaluateStored(stored)));
            }
            case REPLAY_STORED, FAILURE_STORED -> {
                OcraVerificationApplicationService.VerificationCommand.Stored stored =
                        (OcraVerificationApplicationService.VerificationCommand.Stored) scenario.command();
                yield toCanonical(
                        verify.verify(
                                OcraContractRequests.replayStored(stored),
                                new OcraVerificationAuditContext(null, null, null)),
                        stored.otp());
            }
            case REPLAY_INLINE, FAILURE_INLINE -> {
                OcraVerificationApplicationService.VerificationCommand.Inline inline =
                        (OcraVerificationApplicationService.VerificationCommand.Inline) scenario.command();
                yield toCanonical(
                        verify.verify(
                                OcraContractRequests.replayInline(inline),
                                new OcraVerificationAuditContext(null, null, null)),
                        inline.otp());
            }
        };
    }

    private static CanonicalFacadeResult toCanonical(OcraEvaluationApplicationService.EvaluationResult result) {
        boolean includeTrace = result.verboseTrace().isPresent();
        return new CanonicalFacadeResult(
                true, "success", result.otp(), null, null, null, result.suite(), includeTrace, true);
    }

    private static CanonicalFacadeResult toCanonical(OcraVerificationApplicationService.VerificationResult result) {
        boolean success = result.status() == OcraVerificationApplicationService.VerificationStatus.MATCH;
        String reason =
                switch (result.reason()) {
                    case MATCH -> "match";
                    case STRICT_MISMATCH -> "strict_mismatch";
                    case VALIDATION_FAILURE -> "validation_error";
                    case CREDENTIAL_NOT_FOUND -> "credential_not_found";
                    case UNEXPECTED_ERROR -> "unexpected_error";
                };
        boolean includeTrace = result.verboseTrace().isPresent();
        return new CanonicalFacadeResult(
                success,
                reason,
                success ? result.request().otp() : null,
                null,
                null,
                null,
                result.suite(),
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(OcraEvaluationResponse response) {
        return new CanonicalFacadeResult(
                true,
                "success",
                response.otp(),
                null,
                null,
                null,
                response.suite(),
                response.trace() != null,
                response.telemetryId() != null);
    }

    private static CanonicalFacadeResult toCanonical(OcraVerificationResponse response, String submittedOtp) {
        boolean success = "match".equals(response.status());
        OcraVerificationMetadata meta = response.metadata();
        String suite = meta != null ? meta.suite() : null;
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                null,
                suite,
                response.trace() != null,
                meta != null && meta.telemetryId() != null);
    }
}
