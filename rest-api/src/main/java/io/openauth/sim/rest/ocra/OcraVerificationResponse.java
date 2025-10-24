package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("metadata") OcraVerificationMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // DTO bridging service-layer result to REST response payload.
}
