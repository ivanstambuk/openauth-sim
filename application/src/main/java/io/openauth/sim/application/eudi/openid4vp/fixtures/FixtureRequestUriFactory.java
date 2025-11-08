package io.openauth.sim.application.eudi.openid4vp.fixtures;

import io.openauth.sim.application.eudi.openid4vp.OpenId4VpAuthorizationRequestService;
import java.util.Objects;

public final class FixtureRequestUriFactory implements OpenId4VpAuthorizationRequestService.RequestUriFactory {
    private final String baseUri;

    public FixtureRequestUriFactory() {
        this("https://sim.openauth.local/oid4vp/request/");
    }

    public FixtureRequestUriFactory(String baseUri) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
    }

    @Override
    public String create(String requestId) {
        return baseUri + requestId;
    }
}
