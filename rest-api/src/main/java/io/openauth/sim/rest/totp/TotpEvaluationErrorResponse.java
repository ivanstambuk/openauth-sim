package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpEvaluationErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "credential_id_required",
                    "generated",
                    "validated",
                    "credential_not_found",
                    "otp_invalid_format",
                    "otp_out_of_window",
                    "shared_secret_base32_invalid",
                    "shared_secret_conflict",
                    "shared_secret_invalid",
                    "shared_secret_required",
                    "validation_error",
                    "totp_evaluation_failed",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("message")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("details")
        Map<String, Object> details,

        @JsonProperty("trace") VerboseTracePayload trace) {
    // no members
}
