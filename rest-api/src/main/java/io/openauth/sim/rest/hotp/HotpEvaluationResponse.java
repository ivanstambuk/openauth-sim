package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.OtpPreviewResponse;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** REST payload returned by HOTP evaluation endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpEvaluationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "generated",
                    "credential_not_found",
                    "validation_error",
                    "counter_overflow",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("otp") String otp,
        @JsonProperty("previews") List<OtpPreviewResponse> previews,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("metadata")
        HotpEvaluationMetadata metadata,

        @JsonProperty("trace") VerboseTracePayload trace) {

    // Canonical record; no additional behaviour.
}
