package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Phase 1/T005: property-based style tests for secret encoding helpers used by the OCRA domain.
 *
 * <p>Pending implementation of Tasks T009/T010, these tests stay disabled and rely on placeholder
 * assertions. Replace the {@link Disabled} annotation and {@link fail(String)} calls once the
 * normalisation utilities exist.
 */
@Tag("ocra")
@Disabled("Pending Phase 2/T009 secret material helpers")
@TestInstance(Lifecycle.PER_CLASS)
final class OcraSecretMaterialPropertyTest {

  private static final SecureRandom RANDOM = new SecureRandom();

  @DisplayName("hex secrets normalise to lowercase canonical form")
  @RepeatedTest(64)
  void hexSecretsNormaliseToLowercase() {
    String rawSecret = randomAsciiSecret(32);
    String mixedCaseHex = randomisedHexCasing(rawSecret);

    fail("Implement OCRA secret normalisation for hex input: " + mixedCaseHex);
  }

  @DisplayName("base64 secrets round-trip to raw bytes and back")
  @RepeatedTest(64)
  void base64SecretsRoundTrip() {
    byte[] seed = randomBytes(32);
    String base64 = Base64.getEncoder().withoutPadding().encodeToString(seed);

    fail("Implement OCRA secret base64 round-trip for payload: " + base64);
  }

  @DisplayName("invalid encodings surface descriptive errors")
  @RepeatedTest(32)
  void invalidEncodingsAreRejected() {
    String malformedInput = randomMalformedSecret();

    fail("Implement OCRA secret validation failure for input: " + malformedInput);
  }

  private static String randomAsciiSecret(int length) {
    byte[] buffer = randomBytes(length);
    for (int i = 0; i < buffer.length; i++) {
      // Limit to readable ASCII to keep diagnostics friendly.
      buffer[i] = (byte) ('!' + RANDOM.nextInt('~' - '!' + 1));
    }
    return new String(buffer, StandardCharsets.US_ASCII);
  }

  private static String randomisedHexCasing(String input) {
    String hex = HexFormat.of().formatHex(input.getBytes(StandardCharsets.US_ASCII));
    char[] chars = hex.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if (Character.isLetter(chars[i]) && RANDOM.nextBoolean()) {
        chars[i] = Character.toUpperCase(chars[i]);
      }
    }
    return new String(chars);
  }

  private static byte[] randomBytes(int length) {
    byte[] data = new byte[length];
    RANDOM.nextBytes(data);
    return data;
  }

  private static String randomMalformedSecret() {
    // Mix incompatible encodings (hex prefix with non-hex tail, stray padding, whitespace).
    String candidate = randomisedHexCasing(randomAsciiSecret(8));
    return "0x"
        + candidate.substring(0, 6)
        + "--"
        + Base64.getEncoder().encodeToString(randomBytes(4));
  }
}
