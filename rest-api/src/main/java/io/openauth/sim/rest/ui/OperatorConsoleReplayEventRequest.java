package io.openauth.sim.rest.ui;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OperatorConsoleReplayEventRequest(
        @JsonProperty("telemetryId") String telemetryId,
        @JsonProperty("status") String status,
        @JsonProperty("reasonCode") String reasonCode,
        @JsonProperty("reason") String reason,
        @JsonProperty("mode") String mode,
        @JsonProperty("credentialSource") String credentialSource,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("contextFingerprint") String contextFingerprint,
        @JsonProperty("sanitized") Boolean sanitized) {
    // Marker record for operator console replay telemetry payloads.
}
