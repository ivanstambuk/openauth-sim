package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnEvaluationResponse(
        @JsonProperty("status") String status,
        @JsonProperty("assertion") WebAuthnGeneratedAssertion assertion,
        @JsonProperty("metadata") WebAuthnEvaluationMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // DTO marker
}
