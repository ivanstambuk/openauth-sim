package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;

public final class FixtureQrCodeEncoder implements OpenId4VpAuthorizationRequestService.QrCodeEncoder {
    @Override
    public String encode(String payload) {
        return "QR::" + payload;
    }
}
