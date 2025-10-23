package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.Map;

/** Error payload returned when HOTP replay encounters validation or unexpected issues. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayErrorResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("trace") VerboseTracePayload trace) {

    HotpReplayErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
