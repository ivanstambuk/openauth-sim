package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationGenerationApplicationServiceTest {

  private WebAuthnAttestationGenerationApplicationService service;
  private WebAuthnAttestationVector vector;
  private String expectedAttestation;
  private String expectedClientData;

  @BeforeEach
  void setUp() {
    service =
        new WebAuthnAttestationGenerationApplicationService(
            new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"));
    vector =
        WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
            .findFirst()
            .orElseThrow();
    expectedAttestation =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(vector.registration().attestationObject());
    expectedClientData =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(vector.registration().clientDataJson());
  }

  @Test
  void generateSelfSignedAttestationEmitsTelemetry() {
    GenerationCommand.Inline command = selfSignedCommand();

    GenerationResult result = service.generate(command);
    GeneratedAttestation attestation = result.attestation();
    TelemetrySignal telemetry = result.telemetry();

    assertNotNull(attestation);
    assertEquals("public-key", attestation.type());
    assertEquals(vector.vectorId(), attestation.id());
    assertEquals(vector.vectorId(), attestation.rawId());
    assertEquals(vector.vectorId(), attestation.attestationId());
    assertEquals(vector.format(), attestation.format());
    assertEquals(expectedAttestation, attestation.response().attestationObject());
    assertEquals(expectedClientData, attestation.response().clientDataJson());
    assertEquals(true, result.telemetry().fields().get("signatureIncluded"));
    assertEquals(1, telemetry.fields().get("certificateChainCount"));

    assertNotNull(telemetry);
    assertEquals(TelemetryStatus.SUCCESS, telemetry.status());
    assertEquals("generated", telemetry.reasonCode());
    assertEquals(vector.format().label(), telemetry.fields().get("attestationFormat"));
    assertEquals("self_signed", telemetry.fields().get("generationMode"));
    assertEquals(0, telemetry.fields().get("customRootCount"));
  }

  @Test
  void generateUnsignedAttestationFlagsSignatureExclusion() {
    GenerationCommand.Inline command =
        new GenerationCommand.Inline(
            vector.vectorId(),
            vector.format(),
            vector.relyingPartyId(),
            vector.origin(),
            vector.registration().challenge(),
            vector.keyMaterial().credentialPrivateKeyBase64Url(),
            vector.keyMaterial().attestationPrivateKeyBase64Url(),
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
  }

  @Test
  void generateCustomRootAttestationIncludesCertificateChain() throws Exception {
    List<X509Certificate> chain =
        WebAuthnAttestationGeneratorTestHelper.certificateChain(vector.format());
    if (chain.isEmpty()) {
      throw new IllegalStateException("Packed attestation vector missing certificate chain");
    }

    String rootPem = WebAuthnAttestationGeneratorTestHelper.toPem(chain.get(chain.size() - 1));

    GenerationCommand.Inline command =
        new GenerationCommand.Inline(
            vector.vectorId(),
            vector.format(),
            vector.relyingPartyId(),
            vector.origin(),
            vector.registration().challenge(),
            vector.keyMaterial().credentialPrivateKeyBase64Url(),
            vector.keyMaterial().attestationPrivateKeyBase64Url(),
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
  }

  @Test
  void customRootModeWithoutRootsFails() {
    GenerationCommand.Inline command =
        new GenerationCommand.Inline(
            vector.vectorId(),
            vector.format(),
            vector.relyingPartyId(),
            vector.origin(),
            vector.registration().challenge(),
            vector.keyMaterial().credentialPrivateKeyBase64Url(),
            vector.keyMaterial().attestationPrivateKeyBase64Url(),
            vector.keyMaterial().attestationCertificateSerialBase64Url(),
            SigningMode.CUSTOM_ROOT,
            List.of(),
            "");

    assertThrows(IllegalArgumentException.class, () -> service.generate(command));
  }

  private GenerationCommand.Inline selfSignedCommand() {
    return new GenerationCommand.Inline(
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
  }

  /** Helper bridging generator fixtures for certificate expectations. */
  static final class WebAuthnAttestationGeneratorTestHelper {

    private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});

    private WebAuthnAttestationGeneratorTestHelper() {
      // utility
    }

    static List<X509Certificate> certificateChain(WebAuthnAttestationFormat format) {
      WebAuthnAttestationVector vector =
          WebAuthnAttestationFixtures.vectorsFor(format).stream().findFirst().orElseThrow();
      return new WebAuthnAttestationVerifier()
          .verify(
              new WebAuthnAttestationRequest(
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
}
