package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnReplayRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("credentialName") String credentialName,
        @JsonProperty("relyingPartyId") String relyingPartyId,
        @JsonProperty("origin") String origin,
        @JsonProperty("expectedType") String expectedType,
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
