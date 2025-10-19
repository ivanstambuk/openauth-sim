package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Metadata describing the outcome of a HOTP evaluation request. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpEvaluationMetadata(
        @JsonProperty("credentialSource") String credentialSource,
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("credentialReference") Boolean credentialReference,
        @JsonProperty("hashAlgorithm") String hashAlgorithm,
        @JsonProperty("digits") Integer digits,
        @JsonProperty("previousCounter") Long previousCounter,
        @JsonProperty("nextCounter") Long nextCounter,
        @JsonProperty("samplePresetKey") String samplePresetKey,
        @JsonProperty("samplePresetLabel") String samplePresetLabel,
        @JsonProperty("telemetryId") String telemetryId) {

    // Canonical record; no additional behaviour.
}
