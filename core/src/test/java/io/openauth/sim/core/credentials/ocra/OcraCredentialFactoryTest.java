package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.model.SecretEncoding;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("ocra")
final class OcraCredentialFactoryTest {

  private static final Map<String, String> DEFAULT_METADATA = Map.of("issuer", "Example Bank");

  private final OcraCredentialFactory factory = new OcraCredentialFactory();

  @DisplayName("valid OCRA payload samples create descriptors and pass auxiliary validation")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("validPayloads")
  void validPayloadsShouldCreateDescriptors(OcraValidVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            vector.name(),
            vector.ocraSuite(),
            vector.sharedSecret(),
            vector.secretEncoding(),
            vector.counterValue(),
            vector.pinHashHex(),
            vector.allowedDrift(),
            DEFAULT_METADATA);

    OcraCredentialDescriptor descriptor =
        assertDoesNotThrow(() -> factory.createDescriptor(request));

    assertAll(
        () -> assertEquals(vector.name(), descriptor.name()),
        () -> assertEquals(vector.ocraSuite(), descriptor.suite().value()),
        () -> assertEquals(vector.counterValue(), descriptor.counter().orElse(null)),
        () -> assertEquals(vector.secretEncoding(), descriptor.sharedSecret().encoding()),
        () ->
            assertEquals(
                vector.pinHashHex() == null,
                descriptor.pinHash().isEmpty(),
                "pin hash presence should match request"),
        () -> assertEquals(DEFAULT_METADATA, descriptor.metadata()));

    factory.validateChallenge(descriptor, vector.challenge());
    factory.validateSessionInformation(descriptor, vector.sessionInformation());

    if (vector.timestamp() != null) {
      factory.validateTimestamp(descriptor, vector.timestamp(), vector.referenceInstant());
    } else {
      factory.validateTimestamp(descriptor, null, null);
    }
  }

  @DisplayName("malformed OCRA payloads are rejected with descriptive errors")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("invalidPayloads")
  void invalidPayloadsAreRejected(OcraInvalidVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "invalid-" + vector.description().replace(' ', '-').toLowerCase(),
            vector.ocraSuite(),
            vector.sharedSecretInput(),
            vector.secretEncoding(),
            vector.counterValue(),
            vector.pinHashHex(),
            vector.allowedDrift(),
            Map.of());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> factory.createDescriptor(request));

    assertNotNull(exception.getMessage());
    if (vector.expectedErrorSubstring() != null) {
      // Compare case-insensitively to keep assertions stable.
      String message = exception.getMessage().toLowerCase();
      String expected = vector.expectedErrorSubstring().toLowerCase();
      if (!message.contains(expected)) {
        throw new AssertionError(
            "Expected message to contain '%s' but was '%s'".formatted(expected, message));
      }
    }
  }

  @DisplayName("counter and PIN combinations respect suite requirements")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("counterAndPinCombinations")
  void counterAndPinCombinationsEnforceSuiteOptions(OcraCounterPinVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "combo-" + vector.description().replace(' ', '-').toLowerCase(),
            vector.ocraSuite(),
            "31323334",
            SecretEncoding.HEX,
            vector.counterValue(),
            vector.pinHashHex(),
            null,
            Map.of());

    if (vector.expectSuccess()) {
      OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
      assertEquals(vector.counterValue(), descriptor.counter().orElse(null));
      assertEquals(vector.pinHashHex() != null, descriptor.pinHash().isPresent());
    } else {
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> factory.createDescriptor(request));
      if (vector.expectedErrorSubstring() != null) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        String expected = vector.expectedErrorSubstring().toLowerCase();
        if (!message.contains(expected)) {
          throw new AssertionError(
              "Expected message to contain '%s' but was '%s'".formatted(expected, message));
        }
      }
    }
  }

  @DisplayName("timestamp inputs honour the declared drift window")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("timestampDriftVectors")
  void timestampDriftIsValidated(OcraTimestampVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "timestamp-" + vector.description().replace(' ', '-').toLowerCase(),
            vector.ocraSuite(),
            "3132333435363738393031323334353637383930",
            SecretEncoding.HEX,
            null,
            null,
            vector.allowedDriftOverride(),
            Map.of());

    OcraCredentialDescriptor descriptor = factory.createDescriptor(request);

    if (vector.expectedErrorSubstring() == null) {
      factory.validateTimestamp(descriptor, vector.timestamp(), vector.referenceInstant());
    } else {
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  factory.validateTimestamp(
                      descriptor, vector.timestamp(), vector.referenceInstant()));
      String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
      String expected = vector.expectedErrorSubstring().toLowerCase();
      if (!message.contains(expected)) {
        throw new AssertionError(
            "Expected message to contain '%s' but was '%s'".formatted(expected, message));
      }
    }
  }

  private static Stream<OcraValidVector> validPayloads() {
    return Stream.of(
        new OcraValidVector(
            "Counter-based suite with static challenge + PIN",
            "counter-suite",
            "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
            "3132333435363738393031323334353637383930",
            SecretEncoding.HEX,
            1L,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            Duration.ofMinutes(5),
            "12345678",
            null,
            null,
            null),
        new OcraValidVector(
            "Time-based suite with session information",
            "totp-suite",
            "OCRA-1:HOTPT30SHA256-7:QN08-SH512",
            "31323334353637383930313233343536373839304142434445464748495051525354555657585960",
            SecretEncoding.HEX,
            null,
            null,
            Duration.ofSeconds(30),
            "12345678",
            "session:device-login",
            Instant.parse("2025-09-27T12:00:00Z"),
            Instant.parse("2025-09-27T11:59:45Z")),
        new OcraValidVector(
            "Base64 shared secret with numeric challenge",
            "base64-suite",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "X1NpbXVsYXRvck9DUkFf",
            SecretEncoding.BASE64,
            null,
            null,
            null,
            "12345678",
            null,
            null,
            null),
        new OcraValidVector(
            "Raw shared secret with alphanumeric challenge",
            "raw-suite",
            "OCRA-1:HOTP-SHA1-6:QA08",
            "RAW-SECRET",
            SecretEncoding.RAW,
            null,
            null,
            null,
            "ABCDEFGH",
            null,
            null,
            null));
  }

  private static Stream<OcraInvalidVector> invalidPayloads() {
    return Stream.of(
        new OcraInvalidVector(
            "Missing secret material",
            "OCRA-1:HOTP-SHA1-6:C-QN08",
            null,
            SecretEncoding.HEX,
            0L,
            null,
            null,
            "sharedSecretKey"),
        new OcraInvalidVector(
            "Hash suite mismatch between PIN hash and declared suite",
            "OCRA-1:HOTP-SHA256-8:QC08-PSHA256",
            "SGVsbG9Xb3JsZA",
            SecretEncoding.BASE64,
            null,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            null,
            "pinHash must use sha256"),
        new OcraInvalidVector(
            "Counter supplied for pure time-based suite",
            "OCRA-1:HOTPT30SHA512-8:QN10",
            "00112233445566778899AABBCCDDEEFF",
            SecretEncoding.HEX,
            8L,
            null,
            null,
            "counterValue not permitted"));
  }

  private static Stream<OcraCounterPinVector> counterAndPinCombinations() {
    return Stream.of(
        new OcraCounterPinVector(
            "Suite requests counter but caller omitted it",
            "OCRA-1:HOTP-SHA1-6:C-QN08",
            null,
            null,
            false,
            "counterValue required"),
        new OcraCounterPinVector(
            "Suite requiring counter + PIN accepts provided values",
            "OCRA-1:HOTP-SHA1-6:C-QN08-PSHA1",
            3L,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            true,
            null),
        new OcraCounterPinVector(
            "Suite without PIN option rejects supplied hash",
            "OCRA-1:HOTP-SHA1-6:C-QN08",
            2L,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            false,
            "pinHash not permitted"));
  }

  private static Stream<OcraTimestampVector> timestampDriftVectors() {
    Instant base = Instant.parse("2025-09-27T12:00:00Z");
    return Stream.of(
        new OcraTimestampVector(
            "Timestamp within 30 second window",
            "OCRA-1:HOTPT30SHA256-7:QN08",
            base.plusSeconds(20),
            base,
            Duration.ofSeconds(30),
            null),
        new OcraTimestampVector(
            "Timestamp outside allowed drift",
            "OCRA-1:HOTPT30SHA256-7:QN08",
            base.plus(Duration.ofMinutes(3)),
            base,
            Duration.ofSeconds(30),
            "timestamp outside permitted drift"));
  }

  private record OcraValidVector(
      String description,
      String name,
      String ocraSuite,
      String sharedSecret,
      SecretEncoding secretEncoding,
      Long counterValue,
      String pinHashHex,
      Duration allowedDrift,
      String challenge,
      String sessionInformation,
      Instant timestamp,
      Instant referenceInstant) {

    @Override
    public String toString() {
      return description;
    }
  }

  private record OcraInvalidVector(
      String description,
      String ocraSuite,
      String sharedSecretInput,
      SecretEncoding secretEncoding,
      Long counterValue,
      String pinHashHex,
      Duration allowedDrift,
      String expectedErrorSubstring) {

    @Override
    public String toString() {
      return description;
    }
  }

  private record OcraCounterPinVector(
      String description,
      String ocraSuite,
      Long counterValue,
      String pinHashHex,
      boolean expectSuccess,
      String expectedErrorSubstring) {

    @Override
    public String toString() {
      return description;
    }
  }

  private record OcraTimestampVector(
      String description,
      String ocraSuite,
      Instant timestamp,
      Instant referenceInstant,
      Duration allowedDriftOverride,
      String expectedErrorSubstring) {

    @Override
    public String toString() {
      return description;
    }
  }
}
