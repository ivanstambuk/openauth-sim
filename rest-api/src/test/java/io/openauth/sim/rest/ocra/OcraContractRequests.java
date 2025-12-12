package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;

final class OcraContractRequests {

    private OcraContractRequests() {
        throw new AssertionError("No instances");
    }

    static OcraEvaluationRequest evaluateInline(OcraEvaluationApplicationService.EvaluationCommand.Inline inline) {
        return new OcraEvaluationRequest(
                null,
                inline.suite(),
                inline.sharedSecretHex(),
                null,
                inline.challenge(),
                inline.sessionHex(),
                null,
                null,
                inline.pinHashHex(),
                inline.timestampHex(),
                inline.counter(),
                null,
                false);
    }

    static OcraEvaluationRequest evaluateStored(OcraEvaluationApplicationService.EvaluationCommand.Stored stored) {
        return new OcraEvaluationRequest(
                stored.credentialId(),
                null,
                null,
                null,
                stored.challenge(),
                stored.sessionHex(),
                null,
                null,
                stored.pinHashHex(),
                stored.timestampHex(),
                stored.counter(),
                null,
                false);
    }

    static OcraVerificationRequest replayStored(OcraVerificationApplicationService.VerificationCommand.Stored stored) {
        OcraVerificationContext context = new OcraVerificationContext(
                stored.challenge(),
                stored.clientChallenge(),
                stored.serverChallenge(),
                stored.sessionHex(),
                stored.timestampHex(),
                stored.counter(),
                stored.pinHashHex());
        return new OcraVerificationRequest(stored.otp(), stored.credentialId(), null, context, false);
    }

    static OcraVerificationRequest replayInline(OcraVerificationApplicationService.VerificationCommand.Inline inline) {
        OcraVerificationContext context = new OcraVerificationContext(
                inline.challenge(),
                inline.clientChallenge(),
                inline.serverChallenge(),
                inline.sessionHex(),
                inline.timestampHex(),
                inline.counter(),
                inline.pinHashHex());
        OcraVerificationInlineCredential inlineCredential =
                new OcraVerificationInlineCredential(inline.suite(), inline.sharedSecretHex(), null);
        return new OcraVerificationRequest(inline.otp(), null, inlineCredential, context, false);
    }
}
