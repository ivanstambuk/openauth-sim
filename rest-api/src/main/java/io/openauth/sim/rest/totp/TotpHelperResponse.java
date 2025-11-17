package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

record TotpHelperResponse(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("otp") String otp,
        @JsonProperty("generationEpochSeconds") long generationEpochSeconds,
        @JsonProperty("expiresEpochSeconds") long expiresEpochSeconds,
        @JsonProperty("metadata") TotpHelperMetadata metadata) {
    // no members
}
