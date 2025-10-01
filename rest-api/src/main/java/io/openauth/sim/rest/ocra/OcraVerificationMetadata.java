package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationMetadata(
    @JsonProperty("credentialSource") String credentialSource,
    @JsonProperty("suite") String suite,
    @JsonProperty("otpLength") int otpLength,
    @JsonProperty("durationMillis") long durationMillis,
    @JsonProperty("contextFingerprint") String contextFingerprint,
    @JsonProperty("telemetryId") String telemetryId,
    @JsonProperty("outcome") String outcome) {
  // Response metadata consumed by REST/CLI telemetry consumers.
}
