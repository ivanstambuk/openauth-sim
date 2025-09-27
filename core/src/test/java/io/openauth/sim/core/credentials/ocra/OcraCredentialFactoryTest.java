package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 1/T004: skeleton verifying OCRA credential creation/validation once the domain exists.
 *
 * <p>The class is intentionally disabled so the codebase stays green while we finish Phase 2.
 * Remove {@link Disabled} after implementing tasks T007–T010; the methods are structured so the
 * suite fails fast if production logic is missing.
 */
@Tag("ocra")
@Disabled("Pending Phase 2 OCRA credential implementation (tasks T007–T010)")
final class OcraCredentialFactoryTest {

  @DisplayName("valid OCRA payload samples create credentials without error")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("validPayloads")
  void validPayloadsShouldCreateCredentials(OcraValidVector vector) {
    fail("Implement OCRA credential factory to satisfy valid payload: " + vector.description());
  }

  @DisplayName("malformed OCRA payloads are rejected with descriptive errors")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("invalidPayloads")
  void invalidPayloadsAreRejected(OcraInvalidVector vector) {
    fail("Implement OCRA credential validation to reject: " + vector.description());
  }

  @DisplayName("counter and PIN combinations respect suite requirements")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("counterAndPinCombinations")
  void counterAndPinCombinationsEnforceSuiteOptions(OcraCounterPinVector vector) {
    fail("Implement counter/PIN handling for combination: " + vector.description());
  }

  @DisplayName("timestamp inputs honour the declared drift window")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("timestampDriftVectors")
  void timestampDriftIsValidated(OcraTimestampVector vector) {
    fail("Implement timestamp handling for vector: " + vector.description());
  }

  private static Stream<OcraValidVector> validPayloads() {
    return Stream.of(
        new OcraValidVector(
            "Counter-based suite with static challenge + PIN",
            "OCRA-1:HOTP-SHA1-6:QC10-PSHA1",
            "3132333435363738393031323334353637383930",
            1L,
            "5e884898da28047151d0e56f8dc6292773603d0d",
            Duration.ofMinutes(5),
            "1234567890",
            null),
        new OcraValidVector(
            "Time-based suite with session information",
            "OCRA-1:HOTPT30SHA256-7:QN08-SH512",
            "31323334353637383930313233343536373839304142434445464748495051525354555657585960",
            null,
            null,
            Duration.ofSeconds(30),
            "ABCDEF12",
            "session:device-login"));
  }

  private static Stream<OcraInvalidVector> invalidPayloads() {
    return Stream.of(
        new OcraInvalidVector(
            "Missing secret material",
            "OCRA-1:HOTP-SHA1-6:QC10",
            null,
            0L,
            null,
            Instant.parse("2025-09-27T12:00:00Z"),
            "sharedSecretKey missing"),
        new OcraInvalidVector(
            "Hash suite mismatch between PIN hash and declared suite",
            "OCRA-1:HOTP-SHA256-8:QC08-PSHA256",
            "48656C6C6F576F726C64",
            4L,
            "legacy-sha1-hash",
            Instant.parse("2025-09-27T12:00:00Z"),
            "pinHash must use SHA256"),
        new OcraInvalidVector(
            "Counter supplied for pure time-based suite",
            "OCRA-1:HOTPT30SHA512-8:QN10",
            "00112233445566778899AABBCCDDEEFF",
            8L,
            null,
            Instant.parse("2025-09-27T12:05:00Z"),
            "counterValue not permitted"));
  }

  private static Stream<OcraCounterPinVector> counterAndPinCombinations() {
    return Stream.of(
        new OcraCounterPinVector(
            "Suite requests counter but caller omitted it",
            "OCRA-1:HOTP-SHA1-6:QC10",
            null,
            "counterValue required for QC suites"),
        new OcraCounterPinVector(
            "Suite accepts optional PIN hash and it is provided",
            "OCRA-1:HOTP-SHA1-6:QC10-PSHA1",
            "5e884898da28047151d0e56f8dc6292773603d0d",
            null),
        new OcraCounterPinVector(
            "Suite without PIN option rejects supplied hash",
            "OCRA-1:HOTP-SHA1-6:QC10",
            "5e884898da28047151d0e56f8dc6292773603d0d",
            "pinHash not supported"));
  }

  private static Stream<OcraTimestampVector> timestampDriftVectors() {
    return Stream.of(
        new OcraTimestampVector(
            "Timestamp within 30 second window",
            "OCRA-1:HOTPT30SHA256-7:QN08",
            Instant.parse("2025-09-27T12:00:00Z"),
            Duration.ofSeconds(30),
            null),
        new OcraTimestampVector(
            "Timestamp outside allowed drift",
            "OCRA-1:HOTPT30SHA256-7:QN08",
            Instant.parse("2025-09-27T12:04:01Z"),
            Duration.ofSeconds(30),
            "timestamp outside permitted drift"));
  }

  private record OcraValidVector(
      String description,
      String ocraSuite,
      String sharedSecretHex,
      Long counterValue,
      String pinHashHex,
      Duration allowedDrift,
      String challenge,
      String sessionInformation) {

    @Override
    public String toString() {
      return description;
    }
  }

  private record OcraInvalidVector(
      String description,
      String ocraSuite,
      String sharedSecretInput,
      Long counterValue,
      String pinHash,
      Instant timestamp,
      String expectedErrorSubstring) {

    @Override
    public String toString() {
      return description;
    }
  }

  private record OcraCounterPinVector(
      String description, String ocraSuite, String pinHashHex, String expectedErrorSubstring) {

    @Override
    public String toString() {
      return description;
    }
  }

  private record OcraTimestampVector(
      String description,
      String ocraSuite,
      Instant timestamp,
      Duration allowedDrift,
      String expectedErrorSubstring) {

    @Override
    public String toString() {
      return description;
    }
  }
}
