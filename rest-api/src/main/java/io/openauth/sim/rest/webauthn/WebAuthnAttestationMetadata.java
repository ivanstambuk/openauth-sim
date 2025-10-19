package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "WebAuthnAttestationMetadata",
        description = "Telemetry metadata describing the outcome of attestation generation or replay.")
record WebAuthnAttestationMetadata(
        @Schema(description = "Telemetry correlation identifier", example = "rest-fido2-attest-2c66c5f9")
        @JsonProperty("telemetryId")
        String telemetryId,

        @Schema(description = "Reason code associated with the result", example = "generated")
        @JsonProperty("reasonCode")
        String reasonCode,

        @Schema(description = "Attestation format processed by the service", example = "packed")
        @JsonProperty("attestationFormat")
        String attestationFormat,

        @Schema(description = "Reported relying party identifier", example = "example.org")
        @JsonProperty("relyingPartyId")
        String relyingPartyId,

        @Schema(description = "Flag indicating whether trust anchors were provided") @JsonProperty("anchorProvided")
        Boolean anchorProvided,

        @Schema(description = "Flag indicating whether provided trust anchors matched the certificate chain")
        @JsonProperty("anchorTrusted")
        Boolean anchorTrusted,

        @Schema(description = "Flag indicating the verification fell back to self-attestation")
        @JsonProperty("selfAttestedFallback")
        Boolean selfAttestedFallback,

        @Schema(description = "Derived source describing which anchors were used", example = "provided")
        @JsonProperty("anchorSource")
        String anchorSource,

        @Schema(description = "Indicates whether anchors were loaded fresh or retrieved from cache", example = "fresh")
        @JsonProperty("anchorMode")
        String anchorMode,

        @Schema(description = "SHA-256 fingerprint of the attestation certificate when anchors were provided")
        @JsonProperty("certificateFingerprint")
        String certificateFingerprint,

        @Schema(description = "Authenticator AAGUID rendered as a canonical UUID string") @JsonProperty("aaguid")
        String aaguid,

        @Schema(
                description = "Offline metadata entry leveraged when resolving trust anchors",
                example = "mds-w3c-packed-es256")
        @JsonProperty("anchorMetadataEntry")
        String anchorMetadataEntry,

        @Schema(description = "Warnings generated while processing trust anchors") @JsonProperty("anchorWarnings")
        List<String> anchorWarnings,

        @Schema(description = "Indicates whether the generated attestation includes a signature")
        @JsonProperty("signatureIncluded")
        Boolean signatureIncluded,

        @Schema(description = "Number of custom roots provided for generation", example = "1")
        @JsonProperty("customRootCount")
        Integer customRootCount,

        @Schema(description = "Source describing how custom roots were supplied", example = "inline")
        @JsonProperty("customRootSource")
        String customRootSource,

        @Schema(description = "Signing mode applied during generation", example = "self_signed")
        @JsonProperty("generationMode")
        String generationMode,

        @Schema(description = "Input source used to build the request", example = "manual") @JsonProperty("inputSource")
        String inputSource,

        @Schema(description = "Number of certificates returned in the attestation chain", example = "2")
        @JsonProperty("certificateChainCount")
        Integer certificateChainCount,

        @Schema(description = "Attestation certificate chain rendered in PEM format")
        @JsonProperty("certificateChainPem")
        List<String> certificateChainPem) {

    static WebAuthnAttestationMetadata forGeneration(
            String telemetryId,
            String reasonCode,
            String attestationFormat,
            Map<String, Object> telemetryFields,
            List<String> certificateChainPem) {
        return new WebAuthnAttestationMetadata(
                telemetryId,
                reasonCode,
                attestationFormat,
                asString(telemetryFields.get("relyingPartyId")),
                null,
                null,
                null,
                asString(telemetryFields.get("anchorSource")),
                asString(telemetryFields.get("anchorMode")),
                asString(telemetryFields.get("certificateFingerprint")),
                asString(telemetryFields.get("aaguid")),
                asString(telemetryFields.get("anchorMetadataEntry")),
                List.of(),
                asBoolean(telemetryFields.get("signatureIncluded")),
                asInteger(telemetryFields.get("customRootCount")),
                asString(telemetryFields.get("customRootSource")),
                asString(telemetryFields.get("generationMode")),
                asString(telemetryFields.get("inputSource")),
                asInteger(telemetryFields.get("certificateChainCount")),
                certificateChainPem == null ? List.of() : List.copyOf(certificateChainPem));
    }

    static WebAuthnAttestationMetadata forReplay(
            String telemetryId,
            String reasonCode,
            String attestationFormat,
            Map<String, Object> telemetryFields,
            List<String> anchorWarnings) {
        return new WebAuthnAttestationMetadata(
                telemetryId,
                reasonCode,
                attestationFormat,
                asString(telemetryFields.get("relyingPartyId")),
                asBoolean(telemetryFields.get("anchorProvided")),
                asBoolean(telemetryFields.get("anchorTrusted")),
                asBoolean(telemetryFields.get("selfAttestedFallback")),
                asString(telemetryFields.get("anchorSource")),
                asString(telemetryFields.get("anchorMode")),
                asString(telemetryFields.get("certificateFingerprint")),
                asString(telemetryFields.get("aaguid")),
                asString(telemetryFields.get("anchorMetadataEntry")),
                anchorWarnings == null ? List.of() : List.copyOf(anchorWarnings),
                null, // signatureIncluded
                null, // customRootCount
                null, // customRootSource
                null, // generationMode
                null, // inputSource
                null, // certificateChainCount
                null); // certificateChainPem
    }

    private static String asString(Object value) {
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return null;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
