package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
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
            CanonicalScenario nativeScenario = OcraCanonicalScenarios.scenarios(nativeEnv).stream()
                    .filter(s -> s.scenarioId().equals(descriptor.scenarioId()))
                    .findFirst()
                    .orElseThrow();
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
            CanonicalScenario restScenario = OcraCanonicalScenarios.scenarios(restEnv).stream()
                    .filter(s -> s.scenarioId().equals(descriptor.scenarioId()))
                    .findFirst()
                    .orElseThrow();
            OcraEvaluationService restEval = new OcraEvaluationService(new OcraEvaluationApplicationService(
                    Clock.systemUTC(), OcraCredentialResolvers.forStore(restEnv.store())));
            OcraVerificationService restVerify = new OcraVerificationService(new OcraVerificationApplicationService(
                    OcraCredentialResolvers.forVerificationStore(restEnv.store()), restEnv.store()));

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE ->
                            toCanonical(restEval.evaluate(new OcraEvaluationRequest(
                                    null,
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .suite(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .sharedSecretHex(),
                                    null,
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .challenge(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .sessionHex(),
                                    null,
                                    null,
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .pinHashHex(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .timestampHex(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Inline) restScenario.command())
                                            .counter(),
                                    null,
                                    false)));
                        case EVALUATE_STORED ->
                            toCanonical(restEval.evaluate(new OcraEvaluationRequest(
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command())
                                            .credentialId(),
                                    null,
                                    null,
                                    null,
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command())
                                            .challenge(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command())
                                            .sessionHex(),
                                    null,
                                    null,
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command())
                                            .pinHashHex(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command())
                                            .timestampHex(),
                                    ((OcraEvaluationApplicationService.EvaluationCommand.Stored) restScenario.command())
                                            .counter(),
                                    null,
                                    false)));
                        case REPLAY_STORED, FAILURE_STORED -> {
                            OcraVerificationApplicationService.VerificationCommand.Stored stored =
                                    (OcraVerificationApplicationService.VerificationCommand.Stored)
                                            restScenario.command();
                            OcraVerificationContext context = new OcraVerificationContext(
                                    stored.challenge(),
                                    stored.clientChallenge(),
                                    stored.serverChallenge(),
                                    stored.sessionHex(),
                                    stored.timestampHex(),
                                    stored.counter(),
                                    stored.pinHashHex());
                            yield toCanonical(
                                    restVerify.verify(
                                            new OcraVerificationRequest(
                                                    stored.otp(), stored.credentialId(), null, context, false),
                                            new OcraVerificationAuditContext(null, null, null)),
                                    stored.otp());
                        }
                        case REPLAY_INLINE, FAILURE_INLINE -> {
                            OcraVerificationApplicationService.VerificationCommand.Inline inline =
                                    (OcraVerificationApplicationService.VerificationCommand.Inline)
                                            restScenario.command();
                            OcraVerificationContext context = new OcraVerificationContext(
                                    inline.challenge(),
                                    inline.clientChallenge(),
                                    inline.serverChallenge(),
                                    inline.sessionHex(),
                                    inline.timestampHex(),
                                    inline.counter(),
                                    inline.pinHashHex());
                            OcraVerificationInlineCredential inlineCredential = new OcraVerificationInlineCredential(
                                    inline.suite(), inline.sharedSecretHex(), null);
                            yield toCanonical(
                                    restVerify.verify(
                                            new OcraVerificationRequest(
                                                    inline.otp(), null, inlineCredential, context, false),
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
