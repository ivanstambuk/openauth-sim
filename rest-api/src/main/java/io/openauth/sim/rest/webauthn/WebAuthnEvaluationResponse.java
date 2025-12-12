package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnEvaluationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("status")
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("assertion")
        WebAuthnGeneratedAssertion assertion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("metadata")
        WebAuthnEvaluationMetadata metadata,

        @JsonProperty("trace") VerboseTracePayload trace) {
    // DTO marker
}
