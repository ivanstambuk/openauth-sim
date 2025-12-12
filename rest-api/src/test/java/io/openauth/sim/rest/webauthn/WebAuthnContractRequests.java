package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import java.util.Base64;

final class WebAuthnContractRequests {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private WebAuthnContractRequests() {
        throw new AssertionError("No instances");
    }

    static WebAuthnReplayRequest replayStored(WebAuthnReplayApplicationService.ReplayCommand.Stored stored) {
        return new WebAuthnReplayRequest(
                stored.credentialId(),
                null,
                stored.relyingPartyId(),
                stored.origin(),
                stored.expectedType(),
                null,
                null,
                null,
                null,
                encode(stored.expectedChallenge()),
                encode(stored.clientDataJson()),
                encode(stored.authenticatorData()),
                encode(stored.signature()),
                null,
                false);
    }

    static WebAuthnReplayRequest replayInline(WebAuthnReplayApplicationService.ReplayCommand.Inline inline) {
        return new WebAuthnReplayRequest(
                encode(inline.credentialId()),
                inline.credentialName(),
                inline.relyingPartyId(),
                inline.origin(),
                inline.expectedType(),
                encode(inline.publicKeyCose()),
                inline.signatureCounter(),
                inline.userVerificationRequired(),
                inline.algorithm().name(),
                encode(inline.expectedChallenge()),
                encode(inline.clientDataJson()),
                encode(inline.authenticatorData()),
                encode(inline.signature()),
                null,
                false);
    }

    static String encode(byte[] value) {
        return value == null ? "" : URL_ENCODER.encodeToString(value);
    }
}
