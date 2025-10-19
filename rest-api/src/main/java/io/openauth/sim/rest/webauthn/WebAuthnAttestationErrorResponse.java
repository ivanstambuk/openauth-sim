package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(
        name = "WebAuthnAttestationErrorResponse",
        description = "Error payload returned when WebAuthn attestation verification fails.")
record WebAuthnAttestationErrorResponse(
        @Schema(description = "High level status indicator for the failure", example = "invalid")
        @JsonProperty("status")
        String status,

        @Schema(description = "Reason code describing the failure", example = "client_data_challenge_mismatch")
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(description = "Human-readable error message", example = "Client data challenge mismatch.")
        @JsonProperty("message")
        String message,

        @Schema(description = "Additional details about the failure") @JsonProperty("details")
        Map<String, Object> details,

        @Schema(description = "Telemetry metadata captured during verification") @JsonProperty("metadata")
        Map<String, Object> metadata) {

    WebAuthnAttestationErrorResponse {
        details = Map.copyOf(details == null ? Map.of() : details);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
