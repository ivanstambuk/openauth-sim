package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Error payload emitted by HOTP evaluation endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpEvaluationErrorResponse(
        @JsonProperty("status") String status,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {
                    "generated",
                    "credential_not_found",
                    "validation_error",
                    "counter_overflow",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("trace") VerboseTracePayload trace) {

    HotpEvaluationErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
