package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpReplayErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "match",
                    "otp_out_of_window",
                    "credential_not_found",
                    "credential_id_required",
                    "otp_required",
                    "shared_secret_required",
                    "shared_secret_conflict",
                    "shared_secret_base32_invalid",
                    "digits_invalid",
                    "step_seconds_invalid",
                    "drift_invalid",
                    "algorithm_invalid",
                    "validation_error",
                    "totp_replay_failed",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("message")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("details")
        Map<String, Object> details,

        @JsonProperty("trace") VerboseTracePayload trace) {
    TotpReplayErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
