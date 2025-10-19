package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Metadata describing the outcome of a HOTP replay request. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayMetadata(
        @JsonProperty("credentialSource") String credentialSource,
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("credentialReference") Boolean credentialReference,
        @JsonProperty("hashAlgorithm") String hashAlgorithm,
        @JsonProperty("digits") Integer digits,
        @JsonProperty("previousCounter") Long previousCounter,
        @JsonProperty("nextCounter") Long nextCounter,
        @JsonProperty("telemetryId") String telemetryId) {

    // Canonical record; no additional behaviour.
}
