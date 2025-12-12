package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapEvaluationErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "generated",
                    "invalid_input",
                    "unexpected_error",
                    "invalid_request",
                    "missing_field",
                    "invalid_hex",
                    "invalid_number",
                    "invalid_mode",
                    "credential_store_unavailable",
                    "credential_not_found",
                    "emv_cap_evaluation_failed"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("message")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("details")
        Map<String, Object> details,

        @JsonProperty("trace") EmvCapTracePayload trace) {
    // no members
}
