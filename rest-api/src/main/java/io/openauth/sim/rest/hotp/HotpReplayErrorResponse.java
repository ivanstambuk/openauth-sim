package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Error payload returned when HOTP replay encounters validation or unexpected issues. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, String> details) {

    // Canonical record; no additional behaviour.
}
