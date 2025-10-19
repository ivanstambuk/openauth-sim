package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("match") boolean match,
        @JsonProperty("metadata") WebAuthnReplayMetadata metadata) {
    // DTO marker
}
