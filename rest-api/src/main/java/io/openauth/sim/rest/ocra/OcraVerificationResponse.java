package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationResponse(
        @JsonProperty("status") String status,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {
                    "match",
                    "strict_mismatch",
                    "validation_error",
                    "credential_not_found",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("metadata") OcraVerificationMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // DTO bridging service-layer result to REST response payload.
}
