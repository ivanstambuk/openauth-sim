package io.openauth.sim.core.encoding;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility helpers for translating Base32-encoded shared secrets into hexadecimal form.
 * The implementation follows RFC&nbsp;4648 and tolerates whitespace, hyphen separators,
 * and trailing padding characters. All error messages are sanitised to avoid echoing
 * the secret material.
 */
public final class Base32SecretCodec {

    private static final int ASCII_LIMIT = 128;
    private static final int[] DECODE_TABLE = new int[ASCII_LIMIT];

    static {
        Arrays.fill(DECODE_TABLE, -1);
        for (int i = 0; i < 26; i++) {
            DECODE_TABLE['A' + i] = i;
            DECODE_TABLE['a' + i] = i;
        }
        for (int i = 0; i < 6; i++) {
            DECODE_TABLE['2' + i] = 26 + i;
        }
    }

    private Base32SecretCodec() {
        // Utility class
    }

    /**
     * Converts a Base32-encoded value to an uppercase hexadecimal string.
     *
     * @param base32 the Base32 input
     * @return uppercase hexadecimal string
     * @throws IllegalArgumentException if the input is blank or violates the Base32 grammar
     */
    public static String toUpperHex(String base32) {
        Objects.requireNonNull(base32, "base32");

        String normalized = normalize(base32);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Base32 value must not be blank");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(normalized.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch >= ASCII_LIMIT || DECODE_TABLE[ch] < 0) {
                throw new IllegalArgumentException("Base32 value contains invalid characters");
            }
            buffer = (buffer << 5) | DECODE_TABLE[ch];
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                output.write((buffer >> bits) & 0xFF);
                buffer &= (1 << bits) - 1;
            }
        }

        if (bits > 0 && (buffer & ((1 << bits) - 1)) != 0) {
            throw new IllegalArgumentException("Base32 value has invalid trailing bits");
        }

        byte[] bytes = output.toByteArray();
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean encounteredPadding = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isIgnorable(ch)) {
                continue;
            }
            if (ch == '=') {
                encounteredPadding = true;
                continue;
            }
            if (encounteredPadding) {
                if (!isIgnorable(ch)) {
                    throw new IllegalArgumentException("Base32 padding may appear only at the end");
                }
                continue;
            }
            builder.append(Character.toUpperCase(ch));
        }
        return builder.toString();
    }

    private static boolean isIgnorable(char ch) {
        return Character.isWhitespace(ch) || ch == '-' || ch == '\u200B';
    }
}
