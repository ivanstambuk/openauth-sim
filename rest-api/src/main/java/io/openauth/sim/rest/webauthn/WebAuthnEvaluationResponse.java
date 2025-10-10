package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnEvaluationResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reasonCode") String reasonCode,
    @JsonProperty("valid") boolean valid,
    @JsonProperty("metadata") WebAuthnEvaluationMetadata metadata) {
  // DTO marker
}
