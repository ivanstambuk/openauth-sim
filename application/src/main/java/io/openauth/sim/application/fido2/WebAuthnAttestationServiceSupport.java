package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.fido2.WebAuthnVerificationResult;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class WebAuthnAttestationServiceSupport {

    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private WebAuthnAttestationServiceSupport() {
        throw new AssertionError("No instances");
    }

    static Outcome process(
            WebAuthnAttestationVerifier verifier,
            WebAuthnAttestationFormat format,
            String attestationId,
            String relyingPartyId,
            String origin,
            byte[] attestationObject,
            byte[] clientDataJson,
            byte[] expectedChallenge,
            List<X509Certificate> trustAnchors,
            WebAuthnTrustAnchorResolver.Source trustAnchorSource,
            boolean trustAnchorsCached,
            String trustAnchorMetadataEntryId) {

        String sanitizedId = sanitize(attestationId);
        String sanitizedRpId = sanitize(relyingPartyId);
        String sanitizedOrigin = sanitize(origin);

        WebAuthnAttestationRequest request = new WebAuthnAttestationRequest(
                Objects.requireNonNull(format, "format"),
                cloneOrEmpty(attestationObject),
                cloneOrEmpty(clientDataJson),
                cloneOrEmpty(expectedChallenge),
                sanitizedRpId,
                sanitizedOrigin);

        WebAuthnAttestationVerification verification = verifier.verify(request);

        WebAuthnVerificationResult result = verification.result();
        Optional<WebAuthnVerificationError> error = result.error();
        boolean success = result.success();

        List<X509Certificate> certificateChain = verification.certificateChain();
        boolean anchorProvided = trustAnchors != null && !trustAnchors.isEmpty();
        boolean anchorTrusted =
                anchorProvided && !certificateChain.isEmpty() && matchesAnchor(certificateChain, trustAnchors);
        boolean selfAttestedFallback = success && (!anchorProvided || certificateChain.isEmpty() || !anchorTrusted);

        WebAuthnTrustAnchorResolver.Source source =
                trustAnchorSource == null ? WebAuthnTrustAnchorResolver.Source.NONE : trustAnchorSource;
        String anchorSource =
                determineAnchorSource(source, success, anchorProvided, anchorTrusted, selfAttestedFallback);
        String aaguid = formatAaguid(verification.aaguid());

        Optional<CredentialData> credential =
                verification.attestedCredential().map(WebAuthnAttestationServiceSupport::toCredentialData);

        Map<String, Object> telemetryFields = buildTelemetryFields(
                sanitizedId,
                format,
                sanitizedRpId,
                anchorProvided,
                anchorTrusted,
                selfAttestedFallback,
                anchorSource,
                source,
                trustAnchorsCached,
                aaguid,
                certificateChain,
                credential,
                error,
                trustAnchorMetadataEntryId);

        Status status;
        String reasonCode;
        String reason;

        if (success) {
            status = Status.SUCCESS;
            if (selfAttestedFallback) {
                if (anchorProvided && !anchorTrusted) {
                    reasonCode = "anchor_mismatch";
                    reason =
                            "Trust anchors did not match attestation certificate chain; accepted self-attested attestation.";
                } else {
                    reasonCode = "self_attested";
                    reason = "No trust anchors supplied; accepted self-attested attestation.";
                }
            } else {
                reasonCode = "match";
                reason = null;
            }
        } else {
            status = Status.INVALID;
            reasonCode = error.map(err -> err.name().toLowerCase(Locale.US)).orElse("verification_failed");
            reason = result.message();
        }

        return new Outcome(
                status,
                reasonCode,
                reason,
                success,
                error,
                credential,
                aaguid,
                anchorProvided,
                selfAttestedFallback,
                telemetryFields,
                anchorMode(anchorProvided, trustAnchorsCached, anchorTrusted));
    }

    private static CredentialData toCredentialData(WebAuthnStoredCredential credential) {
        return new CredentialData(
                credential.relyingPartyId(),
                BASE64_URL_ENCODER.encodeToString(credential.credentialId()),
                credential.algorithm(),
                credential.userVerificationRequired(),
                credential.signatureCounter());
    }

    private static Map<String, Object> buildTelemetryFields(
            String attestationId,
            WebAuthnAttestationFormat format,
            String relyingPartyId,
            boolean anchorProvided,
            boolean anchorTrusted,
            boolean selfAttestedFallback,
            String anchorSource,
            WebAuthnTrustAnchorResolver.Source anchorSourceType,
            boolean trustAnchorsCached,
            String aaguid,
            List<X509Certificate> certificateChain,
            Optional<CredentialData> credential,
            Optional<WebAuthnVerificationError> error,
            String metadataEntryId) {

        Map<String, Object> fields = new LinkedHashMap<>();
        if (!attestationId.isEmpty()) {
            fields.put("attestationId", attestationId);
        }
        fields.put("attestationFormat", format.label());
        if (!relyingPartyId.isEmpty()) {
            fields.put("relyingPartyId", relyingPartyId);
        }
        fields.put("anchorSource", anchorSource);
        fields.put("anchorProvided", anchorProvided);
        fields.put("anchorTrusted", anchorTrusted);
        fields.put("selfAttestedFallback", selfAttestedFallback);
        fields.put("anchorSourceType", anchorSourceType.name().toLowerCase(Locale.US));
        if (anchorProvided) {
            fields.put("anchorMode", anchorMode(anchorProvided, trustAnchorsCached, anchorTrusted));
        }
        if (metadataEntryId != null && !metadataEntryId.isBlank()) {
            fields.put("anchorMetadataEntry", metadataEntryId);
        }

        credential.ifPresent(data -> {
            fields.put("credentialId", data.credentialId());
            fields.put("algorithm", data.algorithm().label());
            fields.put("userVerificationRequired", data.userVerificationRequired());
            fields.put("signatureCounter", data.signatureCounter());
            if (!data.relyingPartyId().isEmpty() && !fields.containsKey("relyingPartyId")) {
                fields.put("relyingPartyId", data.relyingPartyId());
            }
        });

        if (!aaguid.isEmpty()) {
            fields.put("aaguid", aaguid);
        }

        if (!certificateChain.isEmpty()) {
            fields.put("certificateCount", certificateChain.size());
            if (anchorProvided) {
                String fingerprint = fingerprint(certificateChain.get(0));
                if (!fingerprint.isEmpty()) {
                    fields.put("certificateFingerprint", fingerprint);
                }
            }
        }

        error.ifPresent(err -> fields.put("error", err.name().toLowerCase(Locale.US)));
        return Map.copyOf(fields);
    }

    private static boolean matchesAnchor(List<X509Certificate> certificateChain, List<X509Certificate> trustAnchors) {
        X509Certificate root = certificateChain.get(certificateChain.size() - 1);
        byte[] rootEncoded = encoded(root);
        if (rootEncoded.length == 0) {
            return false;
        }
        for (X509Certificate anchor : trustAnchors) {
            if (MessageDigest.isEqual(rootEncoded, encoded(anchor))) {
                return true;
            }
        }
        return false;
    }

    private static byte[] cloneOrEmpty(byte[] value) {
        return value == null ? new byte[0] : value.clone();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String formatAaguid(byte[] aaguid) {
        if (aaguid == null || aaguid.length != 16) {
            return "";
        }
        String hex = hex(aaguid);
        return "%s-%s-%s-%s-%s"
                .formatted(
                        hex.substring(0, 8),
                        hex.substring(8, 12),
                        hex.substring(12, 16),
                        hex.substring(16, 20),
                        hex.substring(20));
    }

    private static String fingerprint(X509Certificate certificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(certificate.getEncoded());
            return hex(hash);
        } catch (CertificateEncodingException | NoSuchAlgorithmException ex) {
            return "";
        }
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException ex) {
            return new byte[0];
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }
        return builder.toString();
    }

    record CredentialData(
            String relyingPartyId,
            String credentialId,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            long signatureCounter) {
        // Marker record encapsulating credential metadata for telemetry.
    }

    enum Status {
        SUCCESS,
        INVALID,
        ERROR
    }

    record Outcome(
            Status status,
            String reasonCode,
            String reason,
            boolean success,
            Optional<WebAuthnVerificationError> error,
            Optional<CredentialData> credential,
            String aaguid,
            boolean anchorProvided,
            boolean selfAttestedFallback,
            Map<String, Object> telemetryFields,
            String anchorMode) {

        Outcome {
            Objects.requireNonNull(status, "status");
            reasonCode = reasonCode == null ? "unspecified" : reasonCode;
            reason = reason == null ? null : reason.trim();
            error = error == null ? Optional.empty() : error;
            credential = credential == null ? Optional.empty() : credential;
            aaguid = aaguid == null ? "" : aaguid;
            telemetryFields = Objects.requireNonNull(telemetryFields, "telemetryFields");
            anchorMode = anchorMode == null ? "" : anchorMode;
        }
    }

    private static String anchorMode(boolean anchorProvided, boolean cached, boolean trusted) {
        if (!anchorProvided) {
            return "none";
        }
        if (cached) {
            return trusted ? "cached" : "cached_untrusted";
        }
        return trusted ? "fresh" : "fresh_untrusted";
    }

    private static String determineAnchorSource(
            WebAuthnTrustAnchorResolver.Source source,
            boolean success,
            boolean anchorProvided,
            boolean anchorTrusted,
            boolean selfAttestedFallback) {
        if (!anchorProvided || selfAttestedFallback) {
            return "self_attested";
        }
        return switch (source) {
            case METADATA -> anchorTrusted ? "metadata" : (success ? "metadata_unmatched" : "metadata");
            case COMBINED ->
                anchorTrusted ? "metadata_manual" : (success ? "metadata_manual_unmatched" : "metadata_manual");
            case MANUAL, NONE -> anchorTrusted ? "provided" : (success ? "provided_unmatched" : "provided");
        };
    }
}
