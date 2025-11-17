package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.EvaluationWindowRequest;

record TotpHelperRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("window") EvaluationWindowRequest window,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("timestampOverride") Long timestampOverride) {
    // no members
}
