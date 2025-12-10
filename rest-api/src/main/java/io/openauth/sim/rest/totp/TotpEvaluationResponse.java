package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.OtpPreviewResponse;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

record TotpEvaluationResponse(
        @JsonProperty("status") String status,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {
                    "generated",
                    "validated",
                    "credential_not_found",
                    "otp_invalid_format",
                    "otp_out_of_window",
                    "shared_secret_invalid",
                    "validation_error",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("valid") boolean valid,
        @JsonProperty("otp") String otp,
        @JsonProperty("previews") List<OtpPreviewResponse> previews,
        @JsonProperty("metadata") TotpEvaluationMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // no members
}
