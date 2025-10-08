package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

record TotpEvaluationErrorResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reasonCode") String reasonCode,
    @JsonProperty("message") String message,
    @JsonProperty("details") Map<String, Object> details) {
  // no members
}
