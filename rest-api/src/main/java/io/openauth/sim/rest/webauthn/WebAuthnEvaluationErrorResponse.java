package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnEvaluationErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(
                description = "Machine-readable outcome code",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "validation_error",
                    "credential_not_found",
                    "webauthn_evaluation_failed",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("message")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("details")
        Map<String, Object> details,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("metadata")
        Map<String, Object> metadata,

        @JsonProperty("trace") VerboseTracePayload trace) {

    WebAuthnEvaluationErrorResponse {
        details = Map.copyOf(details == null ? Map.of() : details);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
