package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnInlineEvaluationRequest(
    @JsonProperty("credentialName") String credentialName,
    @JsonProperty("relyingPartyId") String relyingPartyId,
    @JsonProperty("origin") String origin,
    @JsonProperty("expectedType") String expectedType,
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("publicKey") String publicKey,
    @JsonProperty("signatureCounter") Long signatureCounter,
    @JsonProperty("userVerificationRequired") Boolean userVerificationRequired,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("expectedChallenge") String expectedChallenge,
    @JsonProperty("clientData") String clientData,
    @JsonProperty("authenticatorData") String authenticatorData,
    @JsonProperty("signature") String signature) {
  // DTO marker
}
