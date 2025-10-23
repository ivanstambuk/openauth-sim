package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

record TotpEvaluationResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("valid") boolean valid,
        @JsonProperty("otp") String otp,
        @JsonProperty("metadata") TotpEvaluationMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // no members
}
