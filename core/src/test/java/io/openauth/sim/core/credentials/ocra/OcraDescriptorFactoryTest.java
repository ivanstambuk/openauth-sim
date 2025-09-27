package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ocra")
final class OcraDescriptorFactoryTest {

  private final OcraCredentialDescriptorFactory factory = new OcraCredentialDescriptorFactory();

  @Test
  @DisplayName("descriptor exposes parsed components for counter + PIN suite")
  void descriptorExposesParsedComponents() {
    OcraCredentialDescriptor descriptor =
        factory.create(
            "demo-token",
            "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
            SecretMaterial.fromHex(
                OcraSecretMaterialSupport.normaliseHex("3132333435363738393031323334353637383930")),
            1L,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            Duration.ofMinutes(5),
            Map.of("issuer", "Example Bank"));

    SecretMaterial sharedSecret = descriptor.sharedSecret();

    assertAll(
        () -> assertEquals("demo-token", descriptor.name()),
        () -> assertEquals("OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1", descriptor.suite().value()),
        () ->
            assertEquals(
                OcraHashAlgorithm.SHA1, descriptor.suite().cryptoFunction().hashAlgorithm()),
        () -> assertEquals(6, descriptor.suite().cryptoFunction().responseDigits()),
        () -> assertTrue(descriptor.suite().cryptoFunction().timeStep().isEmpty()),
        () -> assertTrue(descriptor.suite().dataInput().counter()),
        () ->
            assertEquals(
                OcraChallengeFormat.NUMERIC,
                descriptor.suite().dataInput().challengeQuestion().orElseThrow().format()),
        () ->
            assertEquals(
                8, descriptor.suite().dataInput().challengeQuestion().orElseThrow().length()),
        () -> assertTrue(descriptor.pinHash().isPresent()),
        () ->
            assertEquals(
                OcraHashAlgorithm.SHA1,
                descriptor.pinHashSpecification().orElseThrow().hashAlgorithm()),
        () -> assertEquals(Duration.ofMinutes(5), descriptor.allowedTimestampDrift().orElseThrow()),
        () -> assertEquals(SecretEncoding.HEX, sharedSecret.encoding()),
        () -> assertEquals("3132333435363738393031323334353637383930", sharedSecret.asHex()),
        () -> assertEquals(1L, descriptor.counter().orElseThrow()));
  }

  @Test
  @DisplayName("descriptor parses time-step and session metadata")
  void descriptorParsesTimeStepAndSession() {
    OcraCredentialDescriptor descriptor =
        factory.create(
            "totp-token",
            "OCRA-1:HOTPT30SHA256-7:QN08-SH512",
            SecretMaterial.fromHex(
                OcraSecretMaterialSupport.normaliseHex(
                    "31323334353637383930313233343536373839304142434445464748495051525354555657585960")),
            null,
            null,
            Duration.ofSeconds(30),
            Map.of());

    assertAll(
        () ->
            assertEquals(
                OcraHashAlgorithm.SHA256, descriptor.suite().cryptoFunction().hashAlgorithm()),
        () -> assertEquals(7, descriptor.suite().cryptoFunction().responseDigits()),
        () ->
            assertEquals(
                Duration.ofSeconds(30),
                descriptor.suite().cryptoFunction().timeStep().orElseThrow()),
        () ->
            assertEquals(
                OcraChallengeFormat.NUMERIC,
                descriptor.suite().dataInput().challengeQuestion().orElseThrow().format()),
        () ->
            assertEquals(
                8, descriptor.suite().dataInput().challengeQuestion().orElseThrow().length()),
        () -> assertFalse(descriptor.suite().dataInput().counter()),
        () -> assertTrue(descriptor.pinHash().isEmpty()),
        () -> assertTrue(descriptor.pinHashSpecification().isEmpty()),
        () ->
            assertEquals(
                OcraHashAlgorithm.SHA512,
                descriptor.suite().dataInput().sessionInformation().orElseThrow().hashAlgorithm()),
        () ->
            assertEquals(Duration.ofSeconds(30), descriptor.allowedTimestampDrift().orElseThrow()));
  }

  @Test
  @DisplayName("missing counter when suite demands it is rejected")
  void missingCounterIsRejected() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "counter-missing",
                    "OCRA-1:HOTP-SHA1-6:C-QN08",
                    SecretMaterial.fromHex(
                        OcraSecretMaterialSupport.normaliseHex("3132333435363738")),
                    null,
                    null,
                    null,
                    Map.of()));

    assertTrue(exception.getMessage().contains("counterValue"));
  }

  @Test
  @DisplayName("pin hash must align with suite declaration")
  void mismatchedPinHashIsRejected() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "pin-mismatch",
                    "OCRA-1:HOTP-SHA1-6:QN08",
                    SecretMaterial.fromHex(
                        OcraSecretMaterialSupport.normaliseHex("3132333435363738")),
                    null,
                    "5e884898da28047151d0e56f8dc6292773603d0d",
                    null,
                    Map.of()));

    assertTrue(exception.getMessage().contains("pinHash"));
  }

  @Test
  @DisplayName("invalid hex secret is rejected with descriptive error")
  void invalidSecretIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            factory.create(
                "bad-secret",
                "OCRA-1:HOTP-SHA1-6:QN08",
                OcraSecretMaterialSupport.normaliseSharedSecret(
                    "this-is-not-hex", SecretEncoding.HEX),
                null,
                null,
                null,
                Map.of()));
  }
}
