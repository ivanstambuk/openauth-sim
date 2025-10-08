package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpReplayMetadata(
    @JsonProperty("credentialSource") String credentialSource,
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("credentialReference") Boolean credentialReference,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("digits") Integer digits,
    @JsonProperty("stepSeconds") Long stepSeconds,
    @JsonProperty("driftBackwardSteps") Integer driftBackwardSteps,
    @JsonProperty("driftForwardSteps") Integer driftForwardSteps,
    @JsonProperty("matchedSkewSteps") Integer matchedSkewSteps,
    @JsonProperty("timestampOverrideProvided") Boolean timestampOverrideProvided,
    @JsonProperty("telemetryId") String telemetryId) {
  // metadata container
}
