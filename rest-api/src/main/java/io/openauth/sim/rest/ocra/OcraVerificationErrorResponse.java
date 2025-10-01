package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationErrorResponse(
    @JsonProperty("error") String error,
    @JsonProperty("message") String message,
    @JsonProperty("details") Map<String, ?> details) {
  // Structured error payload returned for validation or processing failures.
}
