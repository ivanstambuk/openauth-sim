package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "WebAuthnAttestationGenerationRequest",
        description = "Request payload for generating a deterministic WebAuthn attestation object.")
record WebAuthnAttestationGenerationRequest(
        @Schema(
                description = "Input source selection",
                example = "PRESET",
                allowableValues = {"PRESET", "MANUAL"})
        @JsonProperty("inputSource")
        String inputSource,

        @Schema(description = "Fixture identifier to generate", example = "w3c-packed-es256")
        @JsonProperty("attestationId")
        String attestationId,

        @Schema(description = "Attestation format label", example = "packed") @JsonProperty("format")
        String format,

        @Schema(description = "Expected relying party ID", example = "example.org") @JsonProperty("relyingPartyId")
        String relyingPartyId,

        @Schema(description = "Expected origin", example = "https://example.org") @JsonProperty("origin")
        String origin,

        @Schema(description = "Challenge encoded as Base64URL") @JsonProperty("challenge")
        String challenge,

        @Schema(description = "Credential private key (JWK or PEM/PKCS#8)") @JsonProperty("credentialPrivateKey")
        String credentialPrivateKey,

        @Schema(description = "Attestation private key (JWK or PEM/PKCS#8)") @JsonProperty("attestationPrivateKey")
        String attestationPrivateKey,

        @Schema(description = "Attestation certificate serial encoded as Base64URL")
        @JsonProperty("attestationCertificateSerial")
        String attestationCertificateSerial,

        @Schema(
                description = "Signing mode selection",
                example = "SELF_SIGNED",
                allowableValues = {"SELF_SIGNED", "UNSIGNED", "CUSTOM_ROOT"})
        @JsonProperty("signingMode")
        String signingMode,

        @Schema(description = "Inline PEM encoded custom root certificates") @JsonProperty("customRootCertificates")
        List<String> customRootCertificates,

        @Schema(description = "Optional seed preset id when overrides are applied") @JsonProperty("seedPresetId")
        String seedPresetId,

        @Schema(description = "Set of edited fields compared to the preset") @JsonProperty("overrides")
        List<String> overrides) {
    // Canonical request record; no additional members required.
}
