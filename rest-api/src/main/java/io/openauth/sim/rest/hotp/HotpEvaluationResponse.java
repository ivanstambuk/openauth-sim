package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

/** REST payload returned by HOTP evaluation endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpEvaluationResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("otp") String otp,
        @JsonProperty("metadata") HotpEvaluationMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {

    // Canonical record; no additional behaviour.
}
