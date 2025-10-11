package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnInlineEvaluationRequest(
    @JsonProperty("credentialName") String credentialName,
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("relyingPartyId") String relyingPartyId,
    @JsonProperty("origin") String origin,
    @JsonProperty("expectedType") String expectedType,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("signatureCounter") Long signatureCounter,
    @JsonProperty("userVerificationRequired") Boolean userVerificationRequired,
    @JsonProperty("challenge") String challenge,
    @JsonProperty("privateKey") String privateKey) {
  // DTO marker
}
