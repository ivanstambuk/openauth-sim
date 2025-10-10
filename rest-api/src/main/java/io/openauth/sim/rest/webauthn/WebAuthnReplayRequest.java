package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

record WebAuthnReplayRequest(
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("relyingPartyId") String relyingPartyId,
    @JsonProperty("origin") String origin,
    @JsonProperty("expectedType") String expectedType,
    @JsonProperty("expectedChallenge") String expectedChallenge,
    @JsonProperty("clientData") String clientData,
    @JsonProperty("authenticatorData") String authenticatorData,
    @JsonProperty("signature") String signature) {
  // DTO marker
}
