package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Error payload returned when HOTP replay encounters validation or unexpected issues. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "match",
                    "otp_mismatch",
                    "credential_not_found",
                    "invalid_hotp_metadata",
                    "metadata_not_supported",
                    "validation_error",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("message")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("details")
        Map<String, Object> details,

        @JsonProperty("trace") VerboseTracePayload trace) {

    HotpReplayErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
