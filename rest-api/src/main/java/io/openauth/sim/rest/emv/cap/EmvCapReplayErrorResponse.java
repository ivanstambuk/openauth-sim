package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.Map;

record EmvCapReplayErrorResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // canonical error envelope
}
