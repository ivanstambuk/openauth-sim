package io.openauth.sim.core.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class Base32SecretCodecTest {

    @Test
    void decodesCanonicalHelloWorld() {
        String hex = Base32SecretCodec.toUpperHex("JBSWY3DPEB3W64TMMQ======");
        assertEquals("48656C6C6F20776F726C64", hex); // "Hello world"
    }

    @Test
    void ignoresWhitespaceHyphenAndCase() {
        String hex = Base32SecretCodec.toUpperHex(" jbsw y3dp-eb3w64tm ");
        assertEquals("48656C6C6F20776F726C", hex);
    }

    @Test
    void rejectsBlankInput() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Base32SecretCodec.toUpperHex("   "));
        assertEquals("Base32 value must not be blank", exception.getMessage());
    }

    @Test
    void rejectsInvalidCharacters() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Base32SecretCodec.toUpperHex("JB$WY3DP"));
        assertEquals("Base32 value contains invalid characters", exception.getMessage());
    }

    @Test
    void rejectsPaddingInMiddle() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Base32SecretCodec.toUpperHex("JBSW=Y3DP"));
        assertEquals("Base32 padding may appear only at the end", exception.getMessage());
    }

    @Test
    void toleratesZeroPaddedTrailingBits() {
        // Single symbol represents only 5 zero bits; decoder produces an empty payload rather than failing.
        String hex = Base32SecretCodec.toUpperHex("A");
        assertEquals("", hex);
    }
}
