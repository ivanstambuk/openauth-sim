package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebAuthnAssertionResponse(
    @JsonProperty("clientDataJSON") String clientDataJson,
    @JsonProperty("authenticatorData") String authenticatorData,
    @JsonProperty("signature") String signature) {
  // DTO marker
}
