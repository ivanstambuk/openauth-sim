package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Manual-mode generation tests for WebAuthn attestation payloads. */
final class WebAuthnAttestationGeneratorManualModeTest {

  private static final byte[] CHALLENGE =
      Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl"); // "test-manual-challenge"

  @Test
  void manualUnsignedBuildsClientDataAndOmitsSignature() {
    WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    WebAuthnAttestationGenerator.GenerationCommand.Manual command =
        new WebAuthnAttestationGenerator.GenerationCommand.Manual(
            WebAuthnAttestationFormat.PACKED,
            "example.org",
            "https://example.org",
            CHALLENGE,
            "cHJpdmF0ZS1rZXktY3JlZC", // dummy base64url
            null,
            null,
            SigningMode.UNSIGNED,
            List.of());

    GenerationResult result = generator.generate(command);

    assertNotNull(result);
    assertEquals("manual", result.attestationId());
    assertEquals(WebAuthnAttestationFormat.PACKED, result.format());
    assertArrayEquals(CHALLENGE, result.expectedChallenge());
    assertFalse(result.signatureIncluded(), "Unsigned mode must omit signature");
    assertTrue(result.certificateChainPem().isEmpty(), "Unsigned mode must omit chain");

    // Basic check that clientDataJSON matches supplied fields.
    String clientData = new String(result.clientDataJson(), StandardCharsets.UTF_8);
    assertTrue(clientData.contains("\"type\":\"webauthn.create\""));
    assertTrue(clientData.contains("\"origin\":\"https://example.org\""));
    String expectedB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(CHALLENGE);
    assertTrue(clientData.contains("\"challenge\":\"" + expectedB64 + "\""));
  }

  @Test
  void manualCustomRootRequiresAtLeastOneCertificate() {
    WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    WebAuthnAttestationGenerator.GenerationCommand.Manual command =
        new WebAuthnAttestationGenerator.GenerationCommand.Manual(
            WebAuthnAttestationFormat.FIDO_U2F,
            "example.org",
            "https://example.org",
            CHALLENGE,
            "cHJpdmF0ZS1rZXktY3JlZC",
            "YXR0ZXN0LXBriy10ZXN0",
            "c2VyaWFsLXRlc3Q",
            SigningMode.CUSTOM_ROOT,
            List.of());

    assertThrows(IllegalArgumentException.class, () -> generator.generate(command));
  }

  @Test
  void manualSelfSignedIncludesCertificateChain() {
    WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    WebAuthnAttestationGenerator.GenerationCommand.Manual command =
        new WebAuthnAttestationGenerator.GenerationCommand.Manual(
            WebAuthnAttestationFormat.ANDROID_KEY,
            "example.org",
            "https://example.org",
            CHALLENGE,
            "cHJpdmF0ZS1rZXktY3JlZC",
            "YXR0ZXN0LXBriy10ZXN0",
            "c2VyaWFsLXRlc3Q",
            SigningMode.SELF_SIGNED,
            List.of());

    GenerationResult result = generator.generate(command);
    assertTrue(result.signatureIncluded(), "Self-signed must include signature");
    assertFalse(result.certificateChainPem().isEmpty(), "Self-signed must include chain");
  }

  @Test
  void manualSignedModesRequireKeys() {
    WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    // Missing attestation key/serial for a signed mode should fail
    WebAuthnAttestationGenerator.GenerationCommand.Manual missingKey =
        new WebAuthnAttestationGenerator.GenerationCommand.Manual(
            WebAuthnAttestationFormat.PACKED,
            "example.org",
            "https://example.org",
            CHALLENGE,
            "cHJpdmF0ZS1rZXktY3JlZC",
            null,
            null,
            SigningMode.SELF_SIGNED,
            List.of());

    assertThrows(IllegalArgumentException.class, () -> generator.generate(missingKey));
  }
}
