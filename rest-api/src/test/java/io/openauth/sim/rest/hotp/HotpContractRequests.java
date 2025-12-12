package io.openauth.sim.rest.hotp;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService;

final class HotpContractRequests {

    private HotpContractRequests() {
        throw new AssertionError("No instances");
    }

    static HotpInlineEvaluationRequest evaluateInline(
            HotpEvaluationApplicationService.EvaluationCommand.Inline inline) {
        return new HotpInlineEvaluationRequest(
                inline.sharedSecretHex(),
                null,
                inline.algorithm().name(),
                inline.digits(),
                null,
                inline.counter(),
                inline.metadata(),
                false);
    }

    static HotpStoredEvaluationRequest evaluateStored(
            HotpEvaluationApplicationService.EvaluationCommand.Stored stored) {
        return new HotpStoredEvaluationRequest(stored.credentialId(), null, false);
    }

    static HotpReplayRequest replayStored(HotpReplayApplicationService.ReplayCommand.Stored stored) {
        return new HotpReplayRequest(stored.credentialId(), null, null, null, null, null, stored.otp(), null, false);
    }

    static HotpReplayRequest replayInline(HotpReplayApplicationService.ReplayCommand.Inline inline) {
        return new HotpReplayRequest(
                null,
                inline.sharedSecretHex(),
                null,
                inline.algorithm().name(),
                inline.digits(),
                inline.counter(),
                inline.otp(),
                null,
                false);
    }
}
