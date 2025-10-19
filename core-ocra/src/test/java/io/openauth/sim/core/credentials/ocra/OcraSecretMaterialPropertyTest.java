package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/** Phase 1/T005: property-based style tests for secret encoding helpers used by the OCRA domain. */
@Tag("ocra")
@TestInstance(Lifecycle.PER_CLASS)
final class OcraSecretMaterialPropertyTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @DisplayName("hex secrets normalise to lowercase canonical form")
    @RepeatedTest(64)
    void hexSecretsNormaliseToLowercase() {
        String rawSecret = randomAsciiSecret(32);
        String mixedCaseHex = randomisedHexCasing(rawSecret);

        SecretMaterial material = OcraSecretMaterialSupport.normaliseSharedSecret(mixedCaseHex, SecretEncoding.HEX);

        String expectedHex = HexFormat.of().formatHex(rawSecret.getBytes(StandardCharsets.US_ASCII));

        assertEquals(SecretEncoding.HEX, material.encoding());
        assertEquals(expectedHex, material.asHex());
    }

    @DisplayName("base64 secrets round-trip to raw bytes and back")
    @RepeatedTest(64)
    void base64SecretsRoundTrip() {
        byte[] seed = randomBytes(32);
        String base64 = Base64.getEncoder().withoutPadding().encodeToString(seed);

        SecretMaterial material = OcraSecretMaterialSupport.normaliseSharedSecret(base64, SecretEncoding.BASE64);

        assertEquals(SecretEncoding.BASE64, material.encoding());
        assertArrayEquals(seed, material.value());
        assertEquals(base64, material.asBase64());
    }

    @DisplayName("invalid encodings surface descriptive errors")
    @RepeatedTest(32)
    void invalidEncodingsAreRejected() {
        String malformedInput = randomMalformedSecret();

        assertThrows(
                IllegalArgumentException.class,
                () -> OcraSecretMaterialSupport.normaliseSharedSecret(malformedInput, SecretEncoding.HEX));
        assertThrows(
                IllegalArgumentException.class,
                () -> OcraSecretMaterialSupport.normaliseSharedSecret(malformedInput, SecretEncoding.BASE64));
        assertThrows(
                IllegalArgumentException.class,
                () -> OcraSecretMaterialSupport.normaliseSharedSecret("  ", SecretEncoding.RAW));
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
        return "0x" + candidate.substring(0, 6) + "--" + Base64.getEncoder().encodeToString(randomBytes(4));
    }
}
