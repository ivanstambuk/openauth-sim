package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpReplayResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "match",
                    "otp_out_of_window",
                    "credential_not_found",
                    "validation_error",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("metadata")
        TotpReplayMetadata metadata,

        @JsonProperty("trace") VerboseTracePayload trace) {
    // canonical record
}
