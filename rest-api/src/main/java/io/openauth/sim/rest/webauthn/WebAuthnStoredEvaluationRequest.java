package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnStoredEvaluationRequest(
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("relyingPartyId") String relyingPartyId,
    @JsonProperty("origin") String origin,
    @JsonProperty("expectedType") String expectedType,
    @JsonProperty("challenge") String challenge,
    @JsonProperty("privateKey") String privateKey,
    @JsonProperty("signatureCounter") Long signatureCounter,
    @JsonProperty("userVerificationRequired") Boolean userVerificationRequired) {
  // DTO marker
}
