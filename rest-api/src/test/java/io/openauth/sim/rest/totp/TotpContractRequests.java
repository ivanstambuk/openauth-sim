package io.openauth.sim.rest.totp;

import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;

final class TotpContractRequests {

    private TotpContractRequests() {
        throw new AssertionError("No instances");
    }

    static TotpInlineEvaluationRequest evaluateInline(
            TotpEvaluationApplicationService.EvaluationCommand.Inline inline) {
        return new TotpInlineEvaluationRequest(
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
                false);
    }

    static TotpStoredEvaluationRequest evaluateStored(
            TotpEvaluationApplicationService.EvaluationCommand.Stored stored) {
        return new TotpStoredEvaluationRequest(
                stored.credentialId(), stored.otp(), stored.evaluationInstant().getEpochSecond(), null, null, false);
    }

    static TotpReplayRequest replayStored(TotpReplayApplicationService.ReplayCommand.Stored stored) {
        return new TotpReplayRequest(
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
                false);
    }

    static TotpReplayRequest replayInline(TotpReplayApplicationService.ReplayCommand.Inline inline) {
        return new TotpReplayRequest(
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
                false);
    }
}
