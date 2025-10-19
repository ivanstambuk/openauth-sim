package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response payload returned by the HOTP replay endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("metadata") HotpReplayMetadata metadata) {

    // Canonical record; no additional behaviour.
}
