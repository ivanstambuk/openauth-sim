package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "WebAuthnAttestationReplayRequest",
        description = "Request payload for replaying a WebAuthn attestation verification with optional trust anchors.")
record WebAuthnAttestationReplayRequest(
        @Schema(description = "Identifier echoed back in telemetry metadata", example = "w3c-packed-es256")
        @JsonProperty("attestationId")
        String attestationId,

        @Schema(
                description = "Attestation statement format label (packed, fido-u2f, tpm, android-key)",
                example = "packed",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("format")
        String format,

        @Schema(
                description = "Expected relying party identifier",
                example = "example.org",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("relyingPartyId")
        String relyingPartyId,

        @Schema(
                description = "Expected origin for the attested client data",
                example = "https://example.org",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("origin")
        String origin,

        @Schema(
                description = "Authenticator attestation object encoded as Base64URL",
                example = "o2NmbXRmcGFja2VkZ2F0dFN0bXQ...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("attestationObject")
        String attestationObject,

        @Schema(
                description = "ClientDataJSON payload encoded as Base64URL",
                example = "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoi...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("clientDataJson")
        String clientDataJson,

        @Schema(
                description = "Expected challenge encoded as Base64URL",
                example = "wRhKX934BF4T3Ef1S2H1pla2ZrWQGPFthw6SVumVIBI",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("expectedChallenge")
        String expectedChallenge,

        @Schema(description = "PEM encoded X.509 trust anchors to validate the attestation certificate chain")
        @JsonProperty("trustAnchors")
        List<String> trustAnchors) {
    // DTO marker
}
