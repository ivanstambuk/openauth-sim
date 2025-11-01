package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.EvaluationWindowRequest;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpStoredEvaluationRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("otp") String otp,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("window") EvaluationWindowRequest window,
        @JsonProperty("timestampOverride") Long timestampOverride,
        @JsonProperty("verbose") Boolean verbose) {
    // no members
}
