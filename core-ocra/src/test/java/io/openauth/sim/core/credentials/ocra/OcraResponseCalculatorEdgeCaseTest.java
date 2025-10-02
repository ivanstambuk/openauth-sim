package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class OcraResponseCalculatorEdgeCaseTest {

  private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final OcraCredentialFactory FACTORY = new OcraCredentialFactory();

  @Test
  @DisplayName("numeric challenges must contain digits only")
  void numericChallengeRequiresDigits() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QN08", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12AB5678", null, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("numeric challenge"));
  }

  @Test
  @DisplayName("hex challenges reject non-hexadecimal characters")
  void hexChallengeRequiresHexCharacters() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QH08", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "G1234567", null, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("hex challenge"));
  }

  @Test
  @DisplayName("blank challenge question input is rejected")
  void blankChallengeQuestionRejected() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(null, "   ", null, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("challenge question"));
  }

  @Test
  @DisplayName("missing challenge components trigger descriptive error")
  void missingChallengeComponentsRejected() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(null, null, null, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("challenge question"));
  }

  @Test
  @DisplayName("alphanumeric challenge input is uppercased before evaluation")
  void alphanumericChallengeUppercasesInput() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA04", null, null);
    OcraResponseCalculator.OcraExecutionContext lowercase =
        new OcraResponseCalculator.OcraExecutionContext(null, "a1b2", null, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext uppercase =
        new OcraResponseCalculator.OcraExecutionContext(null, "A1B2", null, null, null, null, null);

    String lowerOtp = call(descriptor, lowercase);
    String upperOtp = call(descriptor, uppercase);

    assertEquals(upperOtp, lowerOtp);
  }

  @Test
  @DisplayName("character challenges encode ASCII content as uppercase hex")
  void characterChallengeEncodesAscii() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QC04", null, null);
    OcraResponseCalculator.OcraExecutionContext lowercase =
        new OcraResponseCalculator.OcraExecutionContext(null, "ab12", null, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext uppercase =
        new OcraResponseCalculator.OcraExecutionContext(null, "AB12", null, null, null, null, null);

    assertEquals(call(descriptor, uppercase), call(descriptor, lowercase));
  }

  @Test
  @DisplayName("client-only challenge is supported when server component absent")
  void clientChallengeOnlySupported() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA04", null, null);
    OcraResponseCalculator.OcraExecutionContext expected =
        new OcraResponseCalculator.OcraExecutionContext(null, "AB12", null, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext clientOnly =
        new OcraResponseCalculator.OcraExecutionContext(null, null, null, "AB12", null, null, null);

    assertEquals(call(descriptor, expected), call(descriptor, clientOnly));
  }

  @Test
  @DisplayName("server-only challenge is supported when client component absent")
  void serverChallengeOnlySupported() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA04", null, null);
    OcraResponseCalculator.OcraExecutionContext expected =
        new OcraResponseCalculator.OcraExecutionContext(null, "CD34", null, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext serverOnly =
        new OcraResponseCalculator.OcraExecutionContext(null, null, null, null, "CD34", null, null);

    assertEquals(call(descriptor, expected), call(descriptor, serverOnly));
  }

  @Test
  @DisplayName("client and server challenges concatenate when question is absent")
  void concatenatesClientAndServerChallenges() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08", null, null);
    OcraResponseCalculator.OcraExecutionContext combinedQuestion =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "ABCD1234", null, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext splitChallenges =
        new OcraResponseCalculator.OcraExecutionContext(
            null, null, null, "ABCD", "1234", null, null);

    assertEquals(call(descriptor, combinedQuestion), call(descriptor, splitChallenges));
  }

  @Test
  @DisplayName("negative counter values are rejected")
  void negativeCounterRejected() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:C-QN08", 0L, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            -1L, "12345678", null, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("counter value"));
  }

  @Test
  @DisplayName("timestamp-enabled suites require runtime timestamp input")
  void timestampRequiresInput() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08-T1", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("timestamp value"));
  }

  @Test
  @DisplayName("blank timestamp payload is rejected")
  void blankTimestampRejected() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08-T1", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, "   ");

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("timestamp"));
  }

  @Test
  @DisplayName("timestamp inputs are normalised by padding odd-length hex values")
  void timestampOddLengthNormalized() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08-T1", null, null);
    OcraResponseCalculator.OcraExecutionContext oddLength =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, "123");
    OcraResponseCalculator.OcraExecutionContext padded =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, "0123");

    assertEquals(call(descriptor, padded), call(descriptor, oddLength));
  }

  @Test
  @DisplayName("session information shorter than declared length is left-padded")
  void sessionInformationIsLeftPadded() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08-S064", null, null);
    String shortSession = "1A2B3C";
    String paddedSession = leftPadWithZeros(shortSession, 64 * 2);

    OcraResponseCalculator.OcraExecutionContext shortContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", shortSession, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext paddedContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", paddedSession, null, null, null, null);

    assertEquals(call(descriptor, paddedContext), call(descriptor, shortContext));
  }

  @ParameterizedTest(name = "session token {0} pads to {1} bytes")
  @CsvSource({"S128,128", "S256,256", "S512,512"})
  @DisplayName("session token variants pad to declared byte lengths")
  void sessionTokenVariantsPadToDeclaredLength(String token, int lengthBytes) {
    OcraCredentialDescriptor descriptor =
        descriptor("OCRA-1:HOTP-SHA1-6:QA08-" + token, null, null);
    String shortSession = "ABCDEF";
    String paddedSession = leftPadWithZeros(shortSession, lengthBytes * 2);

    OcraResponseCalculator.OcraExecutionContext shortContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", shortSession, null, null, null, null);
    OcraResponseCalculator.OcraExecutionContext paddedContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", paddedSession, null, null, null, null);

    assertEquals(call(descriptor, paddedContext), call(descriptor, shortContext));
  }

  @Test
  @DisplayName("session information exceeding declared length triggers validation error")
  void sessionInformationTooLongRejected() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08-S064", null, null);
    String tooLong = leftPadWithZeros("FF", (64 * 2) + 2);

    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", tooLong, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("session information exceeds"));
  }

  @Test
  @DisplayName("blank session payload is rejected")
  void blankSessionInformationRejected() {
    OcraCredentialDescriptor descriptor = descriptor("OCRA-1:HOTP-SHA1-6:QA08-S064", null, null);
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", "   ", null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> call(descriptor, context));
    assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("session information"));
  }

  @Test
  @DisplayName("stored PIN hash on descriptor bypasses runtime requirement")
  void storedPinHashUsedWhenPresent() {
    String pinHash = leftPadWithZeros("a1b2", 40);
    OcraCredentialDescriptor descriptor =
        descriptor("OCRA-1:HOTP-SHA1-6:QA08-PSHA1", null, pinHash);
    OcraResponseCalculator.OcraExecutionContext noRuntimePin =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, null, null);

    String otp = call(descriptor, noRuntimePin);

    OcraResponseCalculator.OcraExecutionContext runtimePin =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, pinHash, null);
    assertEquals(otp, call(descriptor, runtimePin));
  }

  @Test
  @DisplayName("runtime PIN hashes are normalised for SHA1 suites")
  void pinHashRuntimeNormalizedSha1() {
    OcraCredentialDescriptor descriptor = manualDescriptor("OCRA-1:HOTP-SHA1-6:QA08-PSHA1", null);
    String shortPin = "ab";
    String paddedPin = leftPadWithZeros(shortPin.toUpperCase(Locale.ROOT), 40);

    OcraResponseCalculator.OcraExecutionContext shortContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, shortPin, null);
    OcraResponseCalculator.OcraExecutionContext paddedContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, paddedPin, null);

    assertEquals(call(descriptor, paddedContext), call(descriptor, shortContext));
  }

  @ParameterizedTest(name = "{0} runtime PIN hashes normalize to stored output")
  @CsvSource({"PSHA256,32", "PSHA512,64"})
  @DisplayName("runtime PIN hashes are normalised and padded for SHA256/SHA512 suites")
  void pinHashRuntimeNormalized(String pinToken, int expectedLengthBytes) {
    String suite = "OCRA-1:HOTP-SHA1-6:QA08-" + pinToken;
    String shortPin = "ABC"; // odd length on purpose
    String paddedPin = leftPadWithZeros(shortPin.toUpperCase(Locale.ROOT), expectedLengthBytes * 2);

    OcraCredentialDescriptor descriptor = manualDescriptor(suite, null);
    OcraResponseCalculator.OcraExecutionContext shortContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, shortPin, null);
    OcraResponseCalculator.OcraExecutionContext paddedContext =
        new OcraResponseCalculator.OcraExecutionContext(
            null, "12345678", null, null, null, paddedPin, null);

    assertEquals(call(descriptor, paddedContext), call(descriptor, shortContext));
  }

  private static String call(
      OcraCredentialDescriptor descriptor, OcraResponseCalculator.OcraExecutionContext context) {
    return OcraResponseCalculator.generate(descriptor, context);
  }

  private static OcraCredentialDescriptor descriptor(
      String suite, Long counter, String pinHashHex) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "test-credential",
            suite,
            SECRET_HEX,
            SecretEncoding.HEX,
            counter,
            pinHashHex,
            null,
            Map.of("source", "edge-case-test"));
    return FACTORY.createDescriptor(request);
  }

  private static OcraCredentialDescriptor manualDescriptor(String suiteValue, Long counter) {
    OcraSuite suite = OcraSuiteParser.parse(suiteValue);
    Optional<Long> counterValue = Optional.ofNullable(counter);
    return new OcraCredentialDescriptor(
        "manual-" + suiteValue,
        suite,
        SecretMaterial.fromHex(SECRET_HEX),
        counterValue,
        Optional.empty(),
        Optional.empty(),
        Map.of());
  }

  private static String leftPadWithZeros(String value, int targetLength) {
    if (value.length() >= targetLength) {
      return value;
    }
    StringBuilder builder = new StringBuilder(targetLength);
    for (int idx = value.length(); idx < targetLength; idx++) {
      builder.append('0');
    }
    builder.append(value);
    return builder.toString();
  }
}
