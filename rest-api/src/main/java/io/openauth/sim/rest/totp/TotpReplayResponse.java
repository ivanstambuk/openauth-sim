package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

record TotpReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("metadata") TotpReplayMetadata metadata) {
    // canonical record
}
