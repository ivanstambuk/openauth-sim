package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationCommand;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class WebAuthnAttestationGeneratorTest {

    private static final WebAuthnAttestationVector VECTOR =
            WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing packed attestation vector"));

    @Test
    void generateSelfSignedAttestationMatchesFixture() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        GenerationCommand.Inline command = new GenerationCommand.Inline(
                VECTOR.vectorId(),
                VECTOR.format(),
                VECTOR.relyingPartyId(),
                VECTOR.origin(),
                VECTOR.registration().challenge(),
                VECTOR.keyMaterial().credentialPrivateKeyBase64Url(),
                VECTOR.keyMaterial().attestationPrivateKeyBase64Url(),
                VECTOR.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of());

        GenerationResult result = generator.generate(command);

        assertNotNull(result, "Generation result must not be null");
        assertEquals(VECTOR.vectorId(), result.attestationId());
        assertEquals(VECTOR.format(), result.format());
        assertArrayEquals(VECTOR.registration().attestationObject(), result.attestationObject(), "attestationObject");
        assertArrayEquals(VECTOR.registration().clientDataJson(), result.clientDataJson(), "clientDataJson");
        assertArrayEquals(VECTOR.registration().challenge(), result.expectedChallenge(), "expectedChallenge");
        assertTrue(result.signatureIncluded(), "Signature should be present for self-signed mode");
        assertNotNull(result.certificateChainPem(), "Certificate chain should be present");
    }

    @Test
    void generateCustomRootWithoutCertificatesFails() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        WebAuthnAttestationGenerator.GenerationCommand.Inline command =
                new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                        VECTOR.vectorId(),
                        VECTOR.format(),
                        VECTOR.relyingPartyId(),
                        VECTOR.origin(),
                        VECTOR.registration().challenge(),
                        VECTOR.keyMaterial().credentialPrivateKeyBase64Url(),
                        VECTOR.keyMaterial().attestationPrivateKeyBase64Url(),
                        VECTOR.keyMaterial().attestationCertificateSerialBase64Url(),
                        WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT,
                        List.of());

        assertThrows(IllegalArgumentException.class, () -> generator.generate(command));
    }

    @Test
    void generateWithMismatchedFormatFails() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        WebAuthnAttestationGenerator.GenerationCommand.Inline command =
                new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                        VECTOR.vectorId(),
                        WebAuthnAttestationFormat.FIDO_U2F,
                        VECTOR.relyingPartyId(),
                        VECTOR.origin(),
                        VECTOR.registration().challenge(),
                        VECTOR.keyMaterial().credentialPrivateKeyBase64Url(),
                        VECTOR.keyMaterial().attestationPrivateKeyBase64Url(),
                        VECTOR.keyMaterial().attestationCertificateSerialBase64Url(),
                        SigningMode.SELF_SIGNED,
                        List.of());

        assertThrows(IllegalArgumentException.class, () -> generator.generate(command));
    }

    @Test
    void generateUnsignedAttestationOmitsSignature() {
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        GenerationCommand.Inline command = new GenerationCommand.Inline(
                VECTOR.vectorId(),
                VECTOR.format(),
                VECTOR.relyingPartyId(),
                VECTOR.origin(),
                VECTOR.registration().challenge(),
                VECTOR.keyMaterial().credentialPrivateKeyBase64Url(),
                VECTOR.keyMaterial().attestationPrivateKeyBase64Url(),
                VECTOR.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.UNSIGNED,
                List.of());

        GenerationResult result = generator.generate(command);

        assertFalse(result.signatureIncluded(), "Unsigned mode should omit signature");
        assertTrue(result.certificateChainPem().isEmpty(), "Unsigned mode should not include a chain");
    }

    @Test
    void generateWithCustomRootIncludesProvidedCertificate() throws Exception {
        List<X509Certificate> chain = certificateChain();
        if (chain.isEmpty()) {
            throw new IllegalStateException("Packed attestation fixture missing certificate chain");
        }

        String rootPem = toPem(chain.get(chain.size() - 1));

        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        GenerationCommand.Inline command = new GenerationCommand.Inline(
                VECTOR.vectorId(),
                VECTOR.format(),
                VECTOR.relyingPartyId(),
                VECTOR.origin(),
                VECTOR.registration().challenge(),
                VECTOR.keyMaterial().credentialPrivateKeyBase64Url(),
                VECTOR.keyMaterial().attestationPrivateKeyBase64Url(),
                VECTOR.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                List.of(rootPem));

        GenerationResult result = generator.generate(command);

        assertTrue(result.signatureIncluded(), "Custom root mode should include signature");
        assertFalse(result.certificateChainPem().isEmpty(), "Custom root mode should include chain");
        assertEquals(rootPem, result.certificateChainPem().get(0));
    }

    @ParameterizedTest
    @MethodSource("unsignedFormats")
    void manualUnsignedAttestationOmitsSignature(WebAuthnAttestationFormat format) throws Exception {
        ManualScenario scenario = ManualScenario.forFormat(format, SigningMode.UNSIGNED);
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();

        GenerationResult result = generator.generate(scenario.command());

        assertFalse(result.signatureIncluded(), "Unsigned manual attestation should not include signature metadata");
        assertTrue(result.certificateChainPem().isEmpty(), "Unsigned manual attestation should not include a chain");

        Map<String, Object> attestationStatement = extractAttestationStatement(result.attestationObject());
        assertFalse(
                attestationStatement.containsKey("sig"), "Unsigned manual attestation must omit the attStmt.sig field");
        assertFalse(
                attestationStatement.containsKey("x5c"), "Unsigned manual attestation must omit the attStmt.x5c field");
    }

    @ParameterizedTest
    @MethodSource("signedFormats")
    void manualSelfSignedAttestationVerifies(WebAuthnAttestationFormat format) {
        ManualScenario scenario = ManualScenario.forFormat(format, SigningMode.SELF_SIGNED);
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();

        GenerationResult result = generator.generate(scenario.command());

        assertTrue(result.signatureIncluded(), "Self-signed manual attestation must include a signature");
        assertFalse(
                result.certificateChainPem().isEmpty(), "Self-signed manual attestation must include a certificate");
        assertManualVerificationSuccess(format, scenario, result);
    }

    @ParameterizedTest
    @MethodSource("signedFormats")
    void manualCustomRootAttestationVerifies(WebAuthnAttestationFormat format) {
        ManualScenario scenario = ManualScenario.forFormat(format, SigningMode.CUSTOM_ROOT);
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();

        GenerationResult result = generator.generate(scenario.command());

        assertTrue(result.signatureIncluded(), "Custom-root manual attestation must include a signature");
        assertFalse(
                result.certificateChainPem().isEmpty(),
                "Custom-root manual attestation must include the supplied certificate chain");
        assertManualVerificationSuccess(format, scenario, result);
    }

    private static List<X509Certificate> certificateChain() {
        return certificateChain(VECTOR);
    }

    private static List<X509Certificate> certificateChain(WebAuthnAttestationVector vector) {
        return new WebAuthnAttestationVerifier()
                .verify(new WebAuthnAttestationRequest(
                        vector.format(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        vector.relyingPartyId(),
                        vector.origin()))
                .certificateChain();
    }

    private static String toPem(X509Certificate certificate) throws Exception {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
    }

    private static Stream<Arguments> unsignedFormats() {
        return Stream.of(
                Arguments.of(WebAuthnAttestationFormat.PACKED),
                Arguments.of(WebAuthnAttestationFormat.FIDO_U2F),
                Arguments.of(WebAuthnAttestationFormat.TPM),
                Arguments.of(WebAuthnAttestationFormat.ANDROID_KEY));
    }

    private static Stream<Arguments> signedFormats() {
        return Stream.of(
                Arguments.of(WebAuthnAttestationFormat.PACKED),
                Arguments.of(WebAuthnAttestationFormat.FIDO_U2F),
                Arguments.of(WebAuthnAttestationFormat.TPM),
                Arguments.of(WebAuthnAttestationFormat.ANDROID_KEY));
    }

    private static Map<String, Object> extractAttestationStatement(byte[] attestationObject)
            throws GeneralSecurityException {
        Map<String, Object> decoded = toStringKeyedMap(CborDecoder.decode(attestationObject));
        Object attStmtNode = decoded.get("attStmt");
        if (!(attStmtNode instanceof Map<?, ?>)) {
            throw new IllegalStateException("attStmt must be a CBOR map");
        }
        return toStringKeyedMap(attStmtNode);
    }

    private static Map<String, Object> toStringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("Expected CBOR map but found " + value);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    private static void assertManualVerificationSuccess(
            WebAuthnAttestationFormat format, ManualScenario scenario, GenerationResult result) {
        WebAuthnAttestationRequest request = new WebAuthnAttestationRequest(
                format,
                result.attestationObject(),
                result.clientDataJson(),
                scenario.challengeCopy(),
                scenario.relyingPartyId(),
                scenario.origin());

        WebAuthnAttestationVerification verification = new WebAuthnAttestationVerifier().verify(request);
        if (!verification.result().success()) {
            throw new AssertionError("Manual attestation verification failed: "
                    + verification.result().message());
        }
    }

    private record ManualScenario(
            WebAuthnAttestationFormat format,
            SigningMode signingMode,
            byte[] challenge,
            String relyingPartyId,
            String origin,
            String credentialPrivateKey,
            String attestationPrivateKey,
            String attestationCertificateSerial,
            List<String> customRootCertificates) {

        static ManualScenario forFormat(WebAuthnAttestationFormat format, SigningMode signingMode) {
            WebAuthnAttestationVector vector = vectorFor(format);
            byte[] challenge = challengeFor(format, signingMode);
            String relyingPartyId = vector.relyingPartyId();
            String origin = vector.origin();
            String credentialKey = vector.keyMaterial().credentialPrivateKeyJwk();
            String attestationKey = signingMode == SigningMode.UNSIGNED
                    ? null
                    : vector.keyMaterial().attestationPrivateKeyJwk();
            String certificateSerial = signingMode == SigningMode.UNSIGNED
                    ? null
                    : vector.keyMaterial().attestationCertificateSerialBase64Url();

            List<String> customRoots =
                    switch (signingMode) {
                        case CUSTOM_ROOT -> {
                            List<X509Certificate> chain = certificateChain(vector);
                            if (chain.isEmpty()) {
                                throw new IllegalStateException(
                                        "Fixture certificate chain missing for format " + format.label());
                            }
                            String rootPem;
                            try {
                                rootPem = toPem(chain.get(chain.size() - 1)).trim();
                            } catch (Exception ex) {
                                throw new IllegalStateException("Unable to encode root certificate", ex);
                            }
                            yield List.of(rootPem);
                        }
                        default -> List.of();
                    };

            return new ManualScenario(
                    format,
                    signingMode,
                    challenge,
                    relyingPartyId,
                    origin,
                    credentialKey,
                    attestationKey,
                    certificateSerial,
                    customRoots);
        }

        GenerationCommand.Manual command() {
            return new GenerationCommand.Manual(
                    format,
                    relyingPartyId,
                    origin,
                    challengeCopy(),
                    credentialPrivateKey,
                    attestationPrivateKey,
                    attestationCertificateSerial,
                    signingMode,
                    customRootCertificates);
        }

        byte[] challengeCopy() {
            return challenge.clone();
        }

        static WebAuthnAttestationVector vectorFor(WebAuthnAttestationFormat format) {
            return WebAuthnAttestationFixtures.vectorsFor(format).stream()
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalStateException("Missing attestation vector for format " + format.label()));
        }

        static byte[] challengeFor(WebAuthnAttestationFormat format, SigningMode signingMode) {
            String seed = "manual-" + format.label() + "-" + signingMode.name().toLowerCase(Locale.US) + "-challenge";
            return seed.getBytes(StandardCharsets.UTF_8);
        }
    }
}
