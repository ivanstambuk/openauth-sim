package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.CanonicalScenarios;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.ocra.OcraCanonicalScenarios;
import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossFacadeContract")
class OcraCrossFacadeContractTest {

    @Test
    void ocraCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        List<CanonicalScenario> descriptors =
                OcraCanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario nativeScenario =
                    CanonicalScenarios.scenarioForDescriptor(nativeEnv, descriptor, OcraCanonicalScenarios::scenarios);
            OcraEvaluationApplicationService nativeEval = new OcraEvaluationApplicationService(
                    Clock.systemUTC(), OcraCredentialResolvers.forStore(nativeEnv.store()));
            OcraVerificationApplicationService nativeVerify = new OcraVerificationApplicationService(
                    OcraCredentialResolvers.forVerificationStore(nativeEnv.store()), nativeEnv.store());

            CanonicalFacadeResult nativeResult =
                    switch (nativeScenario.kind()) {
                        case EVALUATE_INLINE, EVALUATE_STORED ->
                            toCanonical(nativeEval.evaluate(
                                    (OcraEvaluationApplicationService.EvaluationCommand) nativeScenario.command()));
                        case REPLAY_INLINE, REPLAY_STORED, FAILURE_INLINE, FAILURE_STORED ->
                            toCanonical(nativeVerify.verify(
                                    (OcraVerificationApplicationService.VerificationCommand) nativeScenario.command()));
                    };
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario restScenario =
                    CanonicalScenarios.scenarioForDescriptor(restEnv, descriptor, OcraCanonicalScenarios::scenarios);
            OcraEvaluationService restEval = new OcraEvaluationService(new OcraEvaluationApplicationService(
                    Clock.systemUTC(), OcraCredentialResolvers.forStore(restEnv.store())));
            OcraVerificationService restVerify = new OcraVerificationService(new OcraVerificationApplicationService(
                    OcraCredentialResolvers.forVerificationStore(restEnv.store()), restEnv.store()));

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE -> {
                            OcraEvaluationApplicationService.EvaluationCommand.Inline inline =
                                    (OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command();
                            yield toCanonical(restEval.evaluate(OcraContractRequests.evaluateInline(inline)));
                        }
                        case EVALUATE_STORED -> {
                            OcraEvaluationApplicationService.EvaluationCommand.Stored stored =
                                    (OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command();
                            yield toCanonical(restEval.evaluate(OcraContractRequests.evaluateStored(stored)));
                        }
                        case REPLAY_STORED, FAILURE_STORED -> {
                            OcraVerificationApplicationService.VerificationCommand.Stored stored =
                                    (OcraVerificationApplicationService.VerificationCommand.Stored)
                                            restScenario.command();
                            yield toCanonical(
                                    restVerify.verify(
                                            OcraContractRequests.replayStored(stored),
                                            new OcraVerificationAuditContext(null, null, null)),
                                    stored.otp());
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            OcraVerificationApplicationService.VerificationCommand.Inline inline =
                                    (OcraVerificationApplicationService.VerificationCommand.Inline)
                                            restScenario.command();
                            yield toCanonical(
                                    restVerify.verify(
                                            OcraContractRequests.replayInline(inline),
                                            new OcraVerificationAuditContext(null, null, null)),
                                    inline.otp());
                        }
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");
        }
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
