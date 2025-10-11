package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnGeneratedAssertion(
    @JsonProperty("id") String id,
    @JsonProperty("rawId") String rawId,
    @JsonProperty("type") String type,
    @JsonProperty("response") WebAuthnAssertionResponse response,
    @JsonProperty("relyingPartyId") String relyingPartyId,
    @JsonProperty("origin") String origin,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("userVerificationRequired") boolean userVerificationRequired,
    @JsonProperty("signatureCounter") long signatureCounter) {
  // DTO marker
}

record WebAuthnAssertionResponse(
    @JsonProperty("clientDataJSON") String clientDataJson,
    @JsonProperty("authenticatorData") String authenticatorData,
    @JsonProperty("signature") String signature) {
  // DTO marker
}
