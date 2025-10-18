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
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationGeneratorTest {

  private static final WebAuthnAttestationVector VECTOR =
      WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Missing packed attestation vector"));

  @Test
  void generateSelfSignedAttestationMatchesFixture() {
    WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    GenerationCommand.Inline command =
        new GenerationCommand.Inline(
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
    assertArrayEquals(
        VECTOR.registration().attestationObject(), result.attestationObject(), "attestationObject");
    assertArrayEquals(
        VECTOR.registration().clientDataJson(), result.clientDataJson(), "clientDataJson");
    assertArrayEquals(
        VECTOR.registration().challenge(), result.expectedChallenge(), "expectedChallenge");
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
    GenerationCommand.Inline command =
        new GenerationCommand.Inline(
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
    GenerationCommand.Inline command =
        new GenerationCommand.Inline(
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

  private static List<X509Certificate> certificateChain() {
    return new WebAuthnAttestationVerifier()
        .verify(
            new WebAuthnAttestationRequest(
                VECTOR.format(),
                VECTOR.registration().attestationObject(),
                VECTOR.registration().clientDataJson(),
                VECTOR.registration().challenge(),
                VECTOR.relyingPartyId(),
                VECTOR.origin()))
        .certificateChain();
  }

  private static String toPem(X509Certificate certificate) throws Exception {
    String encoded =
        Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
    return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
  }
}
