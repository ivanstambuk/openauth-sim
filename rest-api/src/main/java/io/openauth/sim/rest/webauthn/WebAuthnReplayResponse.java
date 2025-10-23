package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnReplayResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("match") boolean match,
        @JsonProperty("metadata") WebAuthnReplayMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // DTO marker
}
