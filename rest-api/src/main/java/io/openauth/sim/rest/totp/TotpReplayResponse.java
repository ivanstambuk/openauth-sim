package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

record TotpReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("metadata") TotpReplayMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // canonical record
}
