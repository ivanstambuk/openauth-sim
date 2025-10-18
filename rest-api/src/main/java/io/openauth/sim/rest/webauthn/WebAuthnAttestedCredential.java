package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "WebAuthnAttestedCredential",
    description = "Credential metadata produced by a successful WebAuthn attestation verification.")
record WebAuthnAttestedCredential(
    @Schema(
            description = "Relying party identifier associated with the credential",
            example = "example.org")
        @JsonProperty("relyingPartyId")
        String relyingPartyId,
    @Schema(
            description = "Credential identifier encoded as Base64URL",
            example = "yab1s0YtAoc_6gxWhiI0-Z8IFygITlEbt3YCAaiQVKU")
        @JsonProperty("credentialId")
        String credentialId,
    @Schema(description = "Authenticator signature algorithm label", example = "ES256")
        @JsonProperty("algorithm")
        String algorithm,
    @Schema(description = "Whether the authenticator enforces user verification")
        @JsonProperty("userVerificationRequired")
        boolean userVerificationRequired,
    @Schema(description = "Authenticator signature counter value") @JsonProperty("signatureCounter")
        long signatureCounter,
    @Schema(
            description = "Authenticator AAGUID rendered as a canonical UUID string",
            example = "6f4d6a94-7a9b-4c82-9c6d-9f83c60b0f14")
        @JsonProperty("aaguid")
        String aaguid) {
  // DTO marker
}
