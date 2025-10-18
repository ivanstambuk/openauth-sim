package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationGenerationApplicationServiceManualTest {

  private WebAuthnAttestationGenerationApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new WebAuthnAttestationGenerationApplicationService(
            new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"));
  }

  @Test
  void manualUnsignedEmitsInputSourceTelemetry() {
    byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");

    var command =
        new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
            WebAuthnAttestationFormat.PACKED,
            "example.org",
            "https://example.org",
            challenge,
            "cHJpdmF0ZS1rZXktY3JlZC",
            null,
            null,
            SigningMode.UNSIGNED,
            List.of(),
            "",
            "",
            List.of());

    GenerationResult result = service.generate(command);

    assertEquals("public-key", result.attestation().type());
    assertEquals("manual", result.attestation().id());
    assertEquals("manual", result.attestation().rawId());
    assertEquals("manual", result.attestation().attestationId());
    assertEquals("manual", result.telemetry().fields().get("inputSource"));
    assertEquals("unsigned", result.telemetry().fields().get("generationMode"));
    assertEquals(0, result.telemetry().fields().get("customRootCount"));
    assertEquals(0, result.telemetry().fields().get("certificateChainCount"));
  }

  @Test
  void manualCustomRootIncludesSeedAndOverridesTelemetry() {
    byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
    String rootPem =
        "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----\n"; // synthetic

    var command =
        new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
            WebAuthnAttestationFormat.FIDO_U2F,
            "example.org",
            "https://example.org",
            challenge,
            "cHJpdmF0ZS1rZXktY3JlZC",
            "YXR0ZXN0LXBriy10ZXN0",
            "c2VyaWFsLXRlc3Q",
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
    assertEquals("preset-123", result.attestation().id());
    assertEquals("preset-123", result.attestation().rawId());
    Object overrides = result.telemetry().fields().get("overrides");
    assertNotNull(overrides);
    assertTrue(overrides.toString().contains("challenge"));
    assertTrue(overrides.toString().contains("origin"));
  }

  @Test
  void manualCustomRootWithoutRootsFails() {
    byte[] challenge = Base64.getUrlDecoder().decode("dGVzdC1tYW51YWwtY2hhbGxlbmdl");
    var command =
        new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
            WebAuthnAttestationFormat.PACKED,
            "example.org",
            "https://example.org",
            challenge,
            "cHJpdmF0ZS1rZXktY3JlZC",
            "YXR0ZXN0LXBriy10ZXN0",
            "c2VyaWFsLXRlc3Q",
            SigningMode.CUSTOM_ROOT,
            List.of(),
            "",
            "",
            List.of());

    assertThrows(IllegalArgumentException.class, () -> service.generate(command));
  }
}
