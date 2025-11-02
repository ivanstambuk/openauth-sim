package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("metadata") EmvCapReplayMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // canonical response envelope
}
