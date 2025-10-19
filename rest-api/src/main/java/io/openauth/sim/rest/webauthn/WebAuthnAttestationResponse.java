package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "WebAuthnAttestationResponse",
        description = "Response payload for WebAuthn attestation verification and replay endpoints.")
@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnAttestationResponse(
        @Schema(description = "High level status indicator (success on verified attestation)", example = "success")
        @JsonProperty("status")
        String status,

        @Schema(description = "Generated attestation payload when generation succeeds")
        @JsonProperty("generatedAttestation")
        WebAuthnGeneratedAttestation generatedAttestation,

        @Schema(description = "Attested credential metadata when verification succeeds")
        @JsonProperty("attestedCredential")
        WebAuthnAttestedCredential attestedCredential,

        @Schema(description = "Telemetry metadata describing the verification outcome") @JsonProperty("metadata")
        WebAuthnAttestationMetadata metadata) {
    // DTO marker
}
