package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("error")
        String error,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("message")
        String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("details")
        Map<String, ?> details) {
    // Structured error payload returned for validation or processing failures.
}
