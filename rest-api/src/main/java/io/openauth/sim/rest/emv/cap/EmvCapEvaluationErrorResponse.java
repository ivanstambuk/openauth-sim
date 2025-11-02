package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapEvaluationErrorResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("trace") EmvCapTracePayload trace) {
    // no members
}
