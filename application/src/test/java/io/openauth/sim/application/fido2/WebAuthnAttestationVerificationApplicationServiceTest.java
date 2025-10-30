package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService.AttestedCredential;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService.TelemetryStatus;
import io.openauth.sim.application.fido2.WebAuthnAttestationVerificationApplicationService.VerificationCommand;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationVerificationApplicationServiceTest {

    private WebAuthnAttestationVerificationApplicationService service;
    private WebAuthnAttestationVector vector;
    private List<X509Certificate> trustAnchors;
    private String expectedCredentialId;
    private String expectedAaguid;
    private String expectedFingerprint;

    @BeforeEach
    void setUp() {
        service = new WebAuthnAttestationVerificationApplicationService(
                new WebAuthnAttestationVerifier(), new Fido2TelemetryAdapter("fido2.attest"));

        vector = WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                .findFirst()
                .orElseThrow();

        WebAuthnAttestationVerification verification = new WebAuthnAttestationVerifier().verify(toRequest(vector));

        trustAnchors = verification.certificateChain().isEmpty()
                ? List.of()
                : List.of(verification
                        .certificateChain()
                        .get(verification.certificateChain().size() - 1));

        expectedCredentialId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(vector.registration().credentialId());
        expectedAaguid = formatAaguid(vector.registration().aaguid());
        expectedFingerprint = verification.certificateChain().isEmpty()
                ? null
                : sha256Hex(verification.certificateChain().get(0));

        if (trustAnchors.isEmpty()) {
            throw new IllegalStateException("Attestation fixture missing trust anchor");
        }
    }

    @Test
    void inlineAttestationWithTrustAnchorsEmitsSanitizedTelemetry() {
        String attestationId = "";
        VerificationCommand.Inline command = new VerificationCommand.Inline(
                attestationId,
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                trustAnchors,
                false,
                WebAuthnTrustAnchorResolver.Source.MANUAL,
                null,
                List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command);

        assertTrue(result.valid());
        assertTrue(result.anchorProvided());
        assertFalse(result.selfAttestedFallback());

        Optional<AttestedCredential> attestedCredential = result.attestedCredential();
        assertTrue(attestedCredential.isPresent());

        AttestedCredential attested = attestedCredential.orElseThrow();
        assertEquals(vector.relyingPartyId(), attested.relyingPartyId());
        assertEquals(expectedCredentialId, attested.credentialId());
        assertEquals(vector.algorithm(), attested.algorithm());
        assertEquals(expectedAaguid, attested.aaguid());

        TelemetrySignal telemetry = result.telemetry();
        assertEquals(TelemetryStatus.SUCCESS, telemetry.status());
        assertEquals("match", telemetry.reasonCode());
        Map<String, Object> fields = telemetry.fields();
        assertEquals(vector.format().label(), fields.get("attestationFormat"));
        assertEquals(vector.relyingPartyId(), fields.get("relyingPartyId"));
        assertEquals(expectedAaguid, fields.get("aaguid"));
        assertEquals("provided", fields.get("anchorSource"));
        assertEquals("manual", fields.get("anchorSourceType"));
        assertEquals("fresh", fields.get("anchorMode"));
        if (expectedFingerprint != null) {
            assertEquals(expectedFingerprint, fields.get("certificateFingerprint"));
        }
        assertFalse(fields.containsKey("attestationObject"));
        assertFalse(fields.containsKey("clientDataJson"));

        TelemetryFrame frame = telemetry.emit(new Fido2TelemetryAdapter("fido2.attest"), "telemetry-attest-success");
        assertEquals("fido2.attest", frame.event());
        assertEquals("success", frame.status());
        assertTrue(frame.sanitized());
        assertEquals("telemetry-attest-success", frame.fields().get("telemetryId"));
    }

    @Test
    void inlineAttestationWithoutAnchorsFallsBackToSelfAttestedTelemetry() {
        VerificationCommand.Inline command = new VerificationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                List.of(),
                false,
                WebAuthnTrustAnchorResolver.Source.NONE,
                null,
                List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command);

        assertTrue(result.valid());
        assertFalse(result.anchorProvided());
        assertTrue(result.selfAttestedFallback());

        TelemetrySignal telemetry = result.telemetry();
        assertEquals(TelemetryStatus.SUCCESS, telemetry.status());
        Map<String, Object> fields = telemetry.fields();
        assertEquals("self_attested", fields.get("anchorSource"));
        assertEquals("none", fields.get("anchorSourceType"));
        assertEquals(vector.format().label(), fields.get("attestationFormat"));
        assertEquals(vector.relyingPartyId(), fields.get("relyingPartyId"));
        assertFalse(fields.containsKey("anchorMode"));
        assertFalse(fields.containsKey("anchorMode"));
        assertFalse(fields.containsKey("attestationObject"));
        assertFalse(fields.containsKey("clientDataJson"));
        assertFalse(fields.containsKey("certificateFingerprint"));
    }

    @Test
    @DisplayName("Attestation verification rejects mismatched trust anchors")
    void attestationRejectsMismatchedAnchors() {
        X509Certificate referenceAnchor = trustAnchors.get(trustAnchors.size() - 1);
        X509Certificate mismatchedAnchor = mismatchedTrustAnchor(referenceAnchor);

        VerificationCommand.Inline command = new VerificationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                List.of(mismatchedAnchor),
                false,
                WebAuthnTrustAnchorResolver.Source.MANUAL,
                null,
                List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command);

        assertFalse(result.valid());
        assertTrue(result.error().isEmpty());
        assertTrue(result.anchorProvided());
        assertFalse(result.selfAttestedFallback());

        TelemetrySignal telemetry = result.telemetry();
        assertEquals(TelemetryStatus.INVALID, telemetry.status());
        assertEquals("anchor_mismatch", telemetry.reasonCode());
    }

    @Test
    void attestationRejectsChallengeMismatch() {
        byte[] tamperedChallenge = vector.registration().challenge().clone();
        tamperedChallenge[0] ^= 0x01;

        VerificationCommand.Inline command = new VerificationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                tamperedChallenge,
                trustAnchors,
                false,
                WebAuthnTrustAnchorResolver.Source.MANUAL,
                null,
                List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command);

        assertFalse(result.valid());
        assertTrue(result.attestedCredential().isEmpty());
        assertEquals(Optional.of(WebAuthnVerificationError.CLIENT_DATA_CHALLENGE_MISMATCH), result.error());
        assertTrue(result.anchorProvided());
        assertFalse(result.selfAttestedFallback());

        TelemetrySignal telemetry = result.telemetry();
        assertEquals(TelemetryStatus.INVALID, telemetry.status());
        assertEquals("client_data_challenge_mismatch", telemetry.reasonCode());
        assertEquals("provided", telemetry.fields().get("anchorSource"));
    }

    private static WebAuthnAttestationRequest toRequest(WebAuthnAttestationVector vector) {
        return new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin());
    }

    private static String formatAaguid(byte[] aaguid) {
        if (aaguid == null || aaguid.length != 16) {
            return "";
        }
        String hex = HexFormat.of().formatHex(aaguid).toUpperCase(Locale.ROOT);
        return "%s-%s-%s-%s-%s"
                .formatted(
                        hex.substring(0, 8),
                        hex.substring(8, 12),
                        hex.substring(12, 16),
                        hex.substring(16, 20),
                        hex.substring(20));
    }

    private static String sha256Hex(X509Certificate certificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(certificate.getEncoded());
            return HexFormat.of().formatHex(hash).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException | java.security.cert.CertificateEncodingException ex) {
            throw new IllegalStateException("Unable to compute certificate fingerprint", ex);
        }
    }

    private static X509Certificate mismatchedTrustAnchor(X509Certificate reference) {
        for (WebAuthnAttestationFormat format : WebAuthnAttestationFormat.values()) {
            for (WebAuthnAttestationVector candidate : WebAuthnAttestationFixtures.vectorsFor(format)) {
                WebAuthnAttestationVerification verification =
                        new WebAuthnAttestationVerifier().verify(toRequest(candidate));
                if (verification.certificateChain().isEmpty()) {
                    continue;
                }
                X509Certificate candidateRoot = verification
                        .certificateChain()
                        .get(verification.certificateChain().size() - 1);
                try {
                    if (!MessageDigest.isEqual(candidateRoot.getEncoded(), reference.getEncoded())) {
                        return candidateRoot;
                    }
                } catch (CertificateEncodingException ex) {
                    throw new IllegalStateException("Unable to compare trust anchors", ex);
                }
            }
        }
        throw new IllegalStateException("Unable to locate mismatched trust anchor");
    }
}
