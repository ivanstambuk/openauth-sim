package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GeneratedAttestation;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationGenerationApplicationServiceTest {

    private WebAuthnAttestationGenerationApplicationService service;
    private WebAuthnAttestationVector vector;
    private String expectedAttestation;
    private String expectedClientData;
    private String expectedCredentialId;
    private List<String> expectedCertificateChain;

    @BeforeEach
    void setUp() {
        service = new WebAuthnAttestationGenerationApplicationService(
                new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"));
        vector = WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                .findFirst()
                .orElseThrow();
        expectedAttestation = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(vector.registration().attestationObject());
        expectedClientData = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(vector.registration().clientDataJson());
        expectedCredentialId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(vector.registration().credentialId());
        try {
            expectedCertificateChain = WebAuthnAttestationGeneratorTestHelper.certificateChain(vector.format()).stream()
                    .map(certificate -> {
                        try {
                            return WebAuthnAttestationGeneratorTestHelper.toPem(certificate);
                        } catch (Exception ex) {
                            throw new IllegalStateException("Unable to encode certificate to PEM", ex);
                        }
                    })
                    .collect(Collectors.toUnmodifiableList());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to prepare expected certificate chain", ex);
        }
    }

    @Test
    void generateSelfSignedAttestationEmitsTelemetry() {
        GenerationCommand.Inline command = selfSignedCommand();

        GenerationResult result = service.generate(command);
        GeneratedAttestation attestation = result.attestation();
        TelemetrySignal telemetry = result.telemetry();

        assertNotNull(attestation);
        assertEquals("public-key", attestation.type());
        assertEquals(expectedCredentialId, attestation.id());
        assertEquals(expectedCredentialId, attestation.rawId());
        assertEquals(vector.vectorId(), attestation.attestationId());
        assertEquals(vector.format(), attestation.format());
        assertEquals(expectedAttestation, attestation.response().attestationObject());
        assertEquals(expectedClientData, attestation.response().clientDataJson());
        assertEquals(true, result.telemetry().fields().get("signatureIncluded"));
        assertEquals(1, telemetry.fields().get("certificateChainCount"));
        assertEquals(expectedCertificateChain, result.certificateChainPem());

        assertNotNull(telemetry);
        assertEquals(TelemetryStatus.SUCCESS, telemetry.status());
        assertEquals("generated", telemetry.reasonCode());
        assertEquals(vector.format().label(), telemetry.fields().get("attestationFormat"));
        assertEquals("self_signed", telemetry.fields().get("generationMode"));
        assertEquals(0, telemetry.fields().get("customRootCount"));
    }

    @Test
    void generateUnsignedAttestationFlagsSignatureExclusion() {
        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.UNSIGNED,
                List.of(),
                "");

        GenerationResult result = service.generate(command);

        assertEquals(false, result.telemetry().fields().get("signatureIncluded"));
        assertEquals("unsigned", result.telemetry().fields().get("generationMode"));
        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
        assertEquals(0, result.telemetry().fields().get("customRootCount"));
        assertEquals(0, result.telemetry().fields().get("certificateChainCount"));
        assertTrue(result.certificateChainPem().isEmpty());
    }

    @Test
    void generateCustomRootAttestationIncludesCertificateChain() throws Exception {
        List<X509Certificate> chain = WebAuthnAttestationGeneratorTestHelper.certificateChain(vector.format());
        if (chain.isEmpty()) {
            throw new IllegalStateException("Packed attestation vector missing certificate chain");
        }

        String rootPem = WebAuthnAttestationGeneratorTestHelper.toPem(chain.get(chain.size() - 1));

        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                List.of(rootPem),
                "inline");

        GenerationResult result = service.generate(command);

        assertEquals(true, result.telemetry().fields().get("signatureIncluded"));
        assertEquals(chain.size(), result.telemetry().fields().get("certificateChainCount"));
        assertEquals("custom_root", result.telemetry().fields().get("generationMode"));
        assertEquals(1, result.telemetry().fields().get("customRootCount"));
        assertEquals("inline", result.telemetry().fields().get("customRootSource"));
        assertEquals(List.of(rootPem), result.certificateChainPem());
    }

    @Test
    void customRootModeWithoutRootsFails() {
        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                List.of(),
                "");

        assertThrows(IllegalArgumentException.class, () -> service.generate(command));
    }

    @Test
    void generateRejectsLegacyBase64PrivateKeys() {
        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyBase64Url(),
                vector.keyMaterial().attestationPrivateKeyBase64Url(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of(),
                "");

        assertThrows(IllegalArgumentException.class, () -> service.generate(command));
    }

    @Test
    void generateSelfSignedAcceptsPemPrivateKeys() throws Exception {
        GenerationCommand.Inline command = selfSignedPemCommand();

        GenerationResult result = service.generate(command);
        GeneratedAttestation attestation = result.attestation();

        assertNotNull(attestation);
        assertEquals(expectedAttestation, attestation.response().attestationObject());
        assertEquals(expectedClientData, attestation.response().clientDataJson());
        assertEquals(expectedCredentialId, attestation.id());
        assertEquals(expectedCredentialId, attestation.rawId());
        assertEquals(vector.vectorId(), attestation.attestationId());
    }

    @Test
    void generateSelfSignedAcceptsJwkPrivateKeys() throws Exception {
        String credentialJwk = Objects.requireNonNull(
                WebAuthnPrivateKeyParser.parse(
                                toPemPrivateKey(
                                        vector.keyMaterial().credentialPrivateKeyBase64Url(), vector.algorithm()),
                                vector.algorithm())
                        .jwkRepresentation(),
                "credentialJwk");
        String attestationJwk = Objects.requireNonNull(
                WebAuthnPrivateKeyParser.parse(
                                toPemPrivateKey(
                                        vector.keyMaterial().attestationPrivateKeyBase64Url(), vector.algorithm()),
                                vector.algorithm())
                        .jwkRepresentation(),
                "attestationJwk");

        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                credentialJwk,
                attestationJwk,
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of(),
                "");

        GenerationResult result = service.generate(command);
        GeneratedAttestation attestation = result.attestation();
        assertEquals(expectedAttestation, attestation.response().attestationObject());
        assertEquals(expectedClientData, attestation.response().clientDataJson());
    }

    private GenerationCommand.Inline selfSignedCommand() {
        return new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyJwk(),
                vector.keyMaterial().attestationPrivateKeyJwk(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of(),
                "");
    }

    private GenerationCommand.Inline selfSignedPemCommand() throws GeneralSecurityException {
        return new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                toPemPrivateKey(vector.keyMaterial().credentialPrivateKeyBase64Url(), vector.algorithm()),
                toPemPrivateKey(vector.keyMaterial().attestationPrivateKeyBase64Url(), vector.algorithm()),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of(),
                "");
    }

    /** Helper bridging generator fixtures for certificate expectations. */
    static final class WebAuthnAttestationGeneratorTestHelper {

        private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});

        private WebAuthnAttestationGeneratorTestHelper() {
            // utility
        }

        static List<X509Certificate> certificateChain(WebAuthnAttestationFormat format) {
            WebAuthnAttestationVector vector = WebAuthnAttestationFixtures.vectorsFor(format).stream()
                    .findFirst()
                    .orElseThrow();
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

        static String toPem(X509Certificate certificate) throws Exception {
            return "-----BEGIN CERTIFICATE-----\n"
                    + MIME_ENCODER.encodeToString(certificate.getEncoded())
                    + "\n-----END CERTIFICATE-----\n";
        }
    }

    private static String toPemPrivateKey(String base64Url, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        if (base64Url == null || base64Url.isBlank()) {
            return "";
        }
        byte[] scalar = Base64.getUrlDecoder().decode(base64Url);
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveFor(algorithm)));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, scalar), spec);
        PrivateKey privateKey = factory.generatePrivate(privateKeySpec);
        return encodePem(privateKey.getEncoded(), "PRIVATE KEY");
    }

    private static String curveFor(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "secp256r1";
            case ES384 -> "secp384r1";
            case ES512 -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported algorithm for PEM conversion: " + algorithm);
        };
    }

    private static String encodePem(byte[] der, String label) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
