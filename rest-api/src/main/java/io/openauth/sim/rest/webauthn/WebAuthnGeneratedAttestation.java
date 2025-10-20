package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "WebAuthnGeneratedAttestation",
        description = "Generated WebAuthn attestation payload returned by the attestation endpoint.")
@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnGeneratedAttestation(
        @Schema(description = "Credential type", example = "public-key") @JsonProperty("type")
        String type,

        @Schema(description = "Credential identifier", example = "Q2Jl...JfA") @JsonProperty("id")
        String id,

        @Schema(description = "Raw credential identifier", example = "Q2Jl...JfA") @JsonProperty("rawId")
        String rawId,

        @Schema(description = "Attestation response payload mirroring WebAuthn assertions") @JsonProperty("response")
        WebAuthnGeneratedAttestation.AttestationResponse response) {

    @Schema(name = "WebAuthnGeneratedAttestationResponse")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AttestationResponse(
            @Schema(description = "ClientDataJSON payload encoded as Base64URL") @JsonProperty("clientDataJSON")
            String clientDataJson,

            @Schema(description = "Attestation object encoded as Base64URL") @JsonProperty("attestationObject")
            String attestationObject) {
        // Nested response payload mirroring WebAuthn assertions.
    }
}
