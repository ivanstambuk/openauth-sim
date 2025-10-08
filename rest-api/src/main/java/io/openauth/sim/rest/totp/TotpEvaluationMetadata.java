package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

record TotpEvaluationMetadata(
    @JsonProperty("credentialSource") String credentialSource,
    @JsonProperty("matchedSkewSteps") int matchedSkewSteps,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("digits") Integer digits,
    @JsonProperty("stepSeconds") long stepSeconds,
    @JsonProperty("driftBackwardSteps") int driftBackwardSteps,
    @JsonProperty("driftForwardSteps") int driftForwardSteps,
    @JsonProperty("timestampOverrideProvided") boolean timestampOverrideProvided,
    @JsonProperty("telemetryId") String telemetryId) {
  // no members
}
