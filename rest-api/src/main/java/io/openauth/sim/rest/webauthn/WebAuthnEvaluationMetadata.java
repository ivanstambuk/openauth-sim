package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnEvaluationMetadata(
    @JsonProperty("telemetryId") String telemetryId,
    @JsonProperty("credentialSource") String credentialSource,
    @JsonProperty("credentialReference") boolean credentialReference,
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("relyingPartyId") String relyingPartyId,
    @JsonProperty("origin") String origin,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("userVerificationRequired") boolean userVerificationRequired,
    @JsonProperty("error") String error) {
  // DTO marker
}
