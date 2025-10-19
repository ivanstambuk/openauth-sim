package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

record WebAuthnReplayErrorResponse(
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("metadata") Map<String, Object> metadata) {

    WebAuthnReplayErrorResponse {
        details = Map.copyOf(details == null ? Map.of() : details);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
