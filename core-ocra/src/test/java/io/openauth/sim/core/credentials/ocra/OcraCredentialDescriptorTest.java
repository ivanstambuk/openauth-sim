package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ocra")
final class OcraCredentialDescriptorTest {

  private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QC08";
  private static final SecretMaterial DEFAULT_SECRET =
      SecretMaterial.fromHex("3132333435363738393031323334353637383930");

  @DisplayName("descriptor rejects blank names after trimming")
  @Test
  void descriptorRejectsBlankNames() {
    OcraSuite suite = OcraSuiteParser.parse(DEFAULT_SUITE);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new OcraCredentialDescriptor(
                    "   ",
                    suite,
                    DEFAULT_SECRET,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Map.of()));

    assertTrue(exception.getMessage().contains("must not be blank"));
  }

  @DisplayName("descriptor trims name and copies metadata defensively")
  @Test
  void descriptorTrimsAndCopiesMetadata() {
    OcraSuite suite = OcraSuiteParser.parse(DEFAULT_SUITE);
    Map<String, String> metadata = new HashMap<>();
    metadata.put("issuer", "Example Bank");

    OcraCredentialDescriptor descriptor =
        new OcraCredentialDescriptor(
            "  Demo Credential  ",
            suite,
            DEFAULT_SECRET,
            Optional.of(0L),
            Optional.empty(),
            Optional.of(Duration.ofSeconds(30)),
            metadata);

    metadata.put("unexpected", "mutation");

    assertEquals("Demo Credential", descriptor.name());
    assertEquals(Map.of("issuer", "Example Bank"), descriptor.metadata());
  }

  @DisplayName("request copies metadata to avoid external mutation")
  @Test
  void requestCopiesMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("issuer", "Example Bank");

    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "credential-a",
            DEFAULT_SUITE,
            DEFAULT_SECRET.asHex(),
            SecretEncoding.HEX,
            null,
            null,
            null,
            metadata);

    metadata.put("issuer", "Mutated");

    assertEquals(Map.of("issuer", "Example Bank"), request.metadata());
  }

  @DisplayName("request defaults null metadata to an empty map")
  @Test
  void requestDefaultsNullMetadataToEmptyMap() {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "credential-b",
            DEFAULT_SUITE,
            DEFAULT_SECRET.asHex(),
            SecretEncoding.HEX,
            null,
            null,
            null,
            null);

    assertTrue(request.metadata().isEmpty());
  }

  @DisplayName("descriptor factory normalises metadata and rejects unexpected counters")
  @Test
  void descriptorFactoryNormalisesInputs() {
    OcraCredentialDescriptorFactory factory = new OcraCredentialDescriptorFactory();

    OcraCredentialDescriptor descriptor =
        factory.create("  with-metadata  ", DEFAULT_SUITE, DEFAULT_SECRET, null, null, null, null);

    assertEquals("with-metadata", descriptor.name());
    assertTrue(descriptor.metadata().isEmpty());

    IllegalArgumentException counterNotPermitted =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "counter-not-allowed",
                    DEFAULT_SUITE,
                    DEFAULT_SECRET,
                    1L,
                    null,
                    null,
                    Map.of()));
    assertTrue(counterNotPermitted.getMessage().contains("not permitted"));
  }

  @DisplayName("descriptor factory rejects blank name, suite, or secret")
  @Test
  void descriptorFactoryRejectsBlankArguments() {
    OcraCredentialDescriptorFactory factory = new OcraCredentialDescriptorFactory();

    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create("   ", DEFAULT_SUITE, DEFAULT_SECRET, null, null, null, Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create("name", "  ", DEFAULT_SECRET, null, null, null, Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create("name", DEFAULT_SUITE, null, null, null, null, Map.of()));
  }

  @DisplayName("descriptor factory enforces PIN hash requirements")
  @Test
  void descriptorFactoryValidatesPinRequirements() {
    OcraCredentialDescriptorFactory factory = new OcraCredentialDescriptorFactory();

    IllegalArgumentException pinRequired =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "pin-required",
                    "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
                    DEFAULT_SECRET,
                    1L,
                    null,
                    null,
                    Map.of()));
    assertTrue(pinRequired.getMessage().contains("pinHash required"));

    IllegalArgumentException pinNotPermitted =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "pin-not-permitted",
                    DEFAULT_SUITE,
                    DEFAULT_SECRET,
                    null,
                    DEFAULT_SECRET.asHex(),
                    null,
                    Map.of()));
    assertTrue(pinNotPermitted.getMessage().contains("pinHash not permitted"));

    IllegalArgumentException invalidLength =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "pin-invalid-length",
                    "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
                    DEFAULT_SECRET,
                    1L,
                    "ABCDEF",
                    null,
                    Map.of()));
    assertTrue(invalidLength.getMessage().contains("must use SHA1"));
  }

  @DisplayName("descriptor factory validates timestamp drift window")
  @Test
  void descriptorFactoryValidatesDriftWindow() {
    OcraCredentialDescriptorFactory factory = new OcraCredentialDescriptorFactory();

    IllegalArgumentException invalidDrift =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                factory.create(
                    "invalid-drift",
                    "OCRA-1:HOTP-SHA1-6:QN08",
                    DEFAULT_SECRET,
                    null,
                    null,
                    Duration.ZERO,
                    Map.of()));
    assertTrue(invalidDrift.getMessage().contains("positive"));
  }
}
