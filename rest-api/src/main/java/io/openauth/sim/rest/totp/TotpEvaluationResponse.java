package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

record TotpEvaluationResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reasonCode") String reasonCode,
    @JsonProperty("valid") boolean valid,
    @JsonProperty("metadata") TotpEvaluationMetadata metadata) {
  // no members
}
