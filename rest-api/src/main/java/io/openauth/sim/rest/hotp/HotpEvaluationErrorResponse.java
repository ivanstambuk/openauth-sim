package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Error payload placeholder for HOTP evaluation endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpEvaluationErrorResponse(
    @JsonProperty("error") String error,
    @JsonProperty("message") String message,
    @JsonProperty("details") Map<String, String> details) {

  HotpEvaluationErrorResponse {
    details =
        details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
  }
}
