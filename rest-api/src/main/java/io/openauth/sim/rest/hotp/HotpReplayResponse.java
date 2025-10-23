package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

/** Response payload returned by the HOTP replay endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("metadata") HotpReplayMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {

    // Canonical record; no additional behaviour.
}
