package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

record TotpReplayErrorResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reasonCode") String reasonCode,
    @JsonProperty("message") String message,
    @JsonProperty("details") Map<String, Object> details) {
  // canonical error envelope
}
