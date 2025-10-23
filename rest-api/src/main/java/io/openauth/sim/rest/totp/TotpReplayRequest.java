package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpReplayRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("otp") String otp,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("timestampOverride") Long timestampOverride,
        @JsonProperty("driftBackward") Integer driftBackward,
        @JsonProperty("driftForward") Integer driftForward,
        @JsonProperty("sharedSecretHex") String sharedSecretHex,
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("digits") Integer digits,
        @JsonProperty("stepSeconds") Long stepSeconds,
        @JsonProperty("verbose") Boolean verbose) {
    // canonical request record
}
