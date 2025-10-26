package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.trace.VerboseTrace;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationGenerationApplicationServiceManualTest {

    private WebAuthnAttestationGenerationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WebAuthnAttestationGenerationApplicationService(
                new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"));
    }

    @Test
    void manualUnsignedEmitsInputSourceTelemetry() {
        byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                WebAuthnAttestationFormat.PACKED,
                "example.org",
                "https://example.org",
                challenge,
                vector.keyMaterial().credentialPrivateKeyJwk(),
                null,
                null,
                SigningMode.UNSIGNED,
                List.of(),
                "",
                "",
                List.of());

        GenerationResult result = service.generate(command);

        assertEquals("public-key", result.attestation().type());
        assertEquals(result.attestation().id(), result.attestation().rawId());
        assertTrue(isBase64Url(result.attestation().id()));
        assertEquals("manual", result.attestation().attestationId());
        assertEquals("manual", result.telemetry().fields().get("inputSource"));
        assertEquals("unsigned", result.telemetry().fields().get("generationMode"));
        assertEquals(0, result.telemetry().fields().get("customRootCount"));
        assertEquals(0, result.telemetry().fields().get("certificateChainCount"));
        assertTrue(result.certificateChainPem().isEmpty());
    }

    @Test
    void manualCustomRootIncludesSeedAndOverridesTelemetry() {
        byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.FIDO_U2F).stream()
                        .findFirst()
                        .orElseThrow();
        List<String> certificateChain = certificateChainPem(vector);
        String rootPem = certificateChain.get(certificateChain.size() - 1);

        var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                WebAuthnAttestationFormat.FIDO_U2F,
                "example.org",
                "https://example.org",
                challenge,
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                List.of(rootPem),
                "inline",
                "preset-123",
                List.of("challenge", "origin"));

        GenerationResult result = service.generate(command);

        assertEquals("custom_root", result.telemetry().fields().get("generationMode"));
        assertEquals("manual", result.telemetry().fields().get("inputSource"));
        assertEquals("preset-123", result.telemetry().fields().get("seedPresetId"));
        assertEquals("public-key", result.attestation().type());
        assertEquals(result.attestation().id(), result.attestation().rawId());
        assertTrue(isBase64Url(result.attestation().id()));
        assertEquals(List.of(rootPem.trim()), result.certificateChainPem());
        Object overrides = result.telemetry().fields().get("overrides");
        assertNotNull(overrides);
        assertTrue(overrides.toString().contains("challenge"));
        assertTrue(overrides.toString().contains("origin"));
    }

    @Test
    void manualCustomRootTelemetryEmitsRpIdAaguidAndCertificateFingerprint() {
        byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();
        List<String> certificateChain = certificateChainPem(vector);
        String rootPem = certificateChain.get(certificateChain.size() - 1);

        var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                WebAuthnAttestationFormat.PACKED,
                "example.org",
                "https://example.org",
                challenge,
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                List.of(rootPem),
                "inline",
                "",
                List.of());

        GenerationResult result = service.generate(command);

        byte[] attestationObject =
                Base64.getUrlDecoder().decode(result.attestation().response().attestationObject());
        byte[] clientDataJson =
                Base64.getUrlDecoder().decode(result.attestation().response().clientDataJson());
        WebAuthnAttestationVerification verification = new WebAuthnAttestationVerifier()
                .verify(new WebAuthnAttestationRequest(
                        WebAuthnAttestationFormat.PACKED,
                        attestationObject,
                        clientDataJson,
                        challenge,
                        "example.org",
                        "https://example.org"));
        assertTrue(verification.result().success());

        String expectedAaguid = formatAaguid(verification.aaguid());
        String expectedFingerprint = fingerprint(verification.certificateChain().get(0));

        assertEquals("example.org", result.telemetry().fields().get("relyingPartyId"));
        assertEquals(expectedAaguid, result.telemetry().fields().get("aaguid"));
        assertEquals(expectedFingerprint, result.telemetry().fields().get("certificateFingerprint"));
    }

    @Test
    void manualAttestationExposesVerboseTraceSteps() throws Exception {
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();
        byte[] challenge = vector.registration().challenge();

        var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                challenge,
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of(),
                "preset",
                "sample-001",
                List.of());

        Method generateVerbose;
        try {
            generateVerbose = WebAuthnAttestationGenerationApplicationService.class.getMethod(
                    "generate", WebAuthnAttestationGenerationApplicationService.GenerationCommand.class, boolean.class);
        } catch (NoSuchMethodException ex) {
            fail("Feature 035 requires WebAuthnAttestationGenerationApplicationService.generate(command, verbose) "
                    + "to return generation traces when verbose mode is enabled.");
            return;
        }

        Object result = generateVerbose.invoke(service, command, true);
        Method verboseTraceMethod;
        try {
            verboseTraceMethod = result.getClass().getMethod("verboseTrace");
        } catch (NoSuchMethodException ex) {
            fail("WebAuthn attestation generation results must expose verboseTrace() to describe build steps.");
            return;
        }

        @SuppressWarnings("unchecked")
        Optional<VerboseTrace> trace = (Optional<VerboseTrace>) verboseTraceMethod.invoke(result);
        assertTrue(trace.isPresent(), "Verbose trace should be populated when verbose=true");
        VerboseTrace verboseTrace = trace.orElseThrow();
        assertEquals("fido2.attestation.generate", verboseTrace.operation());
        assertNotNull(findStep(verboseTrace, "build.clientData"));
        assertNotNull(findStep(verboseTrace, "build.authenticatorData"));
        assertNotNull(findStep(verboseTrace, "build.signatureBase"));
        assertNotNull(findStep(verboseTrace, "generate.signature"));
        assertNotNull(findStep(verboseTrace, "compose.attestationObject"));
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElse(null);
    }

    @Test
    void manualCustomRootWithoutRootsFails() {
        byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();
        var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                WebAuthnAttestationFormat.PACKED,
                "example.org",
                "https://example.org",
                challenge,
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                List.of(),
                "",
                "",
                List.of());

        assertThrows(IllegalArgumentException.class, () -> service.generate(command));
    }

    @Test
    void manualModeRejectsLegacyBase64CredentialKey() {
        byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
        var command = new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                WebAuthnAttestationFormat.ANDROID_KEY,
                "example.org",
                "https://example.org",
                challenge,
                "cHJpdmF0ZS1rZXktY3JlZC",
                "YXR0ZXN0LXBriy10ZXN0",
                "c2VyaWFsLXRlc3Q",
                SigningMode.SELF_SIGNED,
                List.of(),
                "",
                "",
                List.of());

        assertThrows(IllegalArgumentException.class, () -> service.generate(command));
    }

    private static boolean isBase64Url(String value) {
        try {
            Base64.getUrlDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static List<String> certificateChainPem(WebAuthnAttestationVector vector) {
        WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
        WebAuthnAttestationVerification verification = verifier.verify(new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin()));
        if (!verification.result().success()) {
            throw new IllegalStateException("Attestation verification failed for vector "
                    + vector.vectorId()
                    + ": "
                    + verification.result().message());
        }
        return verification.certificateChain().stream()
                .map(certificate -> {
                    try {
                        return toPem(certificate);
                    } catch (CertificateEncodingException ex) {
                        throw new IllegalStateException("Unable to encode certificate", ex);
                    }
                })
                .toList();
    }

    private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
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
            throw new IllegalStateException("Unable to compute certificate fingerprint", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }
}
