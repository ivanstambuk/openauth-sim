package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnEvaluationResponse(
    @JsonProperty("status") String status,
    @JsonProperty("assertion") WebAuthnGeneratedAssertion assertion,
    @JsonProperty("metadata") WebAuthnEvaluationMetadata metadata) {
  // DTO marker
}
