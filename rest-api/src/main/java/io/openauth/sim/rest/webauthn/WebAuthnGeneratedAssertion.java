package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnGeneratedAssertion(
    @JsonProperty("type") String type,
    @JsonProperty("id") String id,
    @JsonProperty("rawId") String rawId,
    @JsonProperty("response") WebAuthnAssertionResponse response) {
  // DTO marker
}
