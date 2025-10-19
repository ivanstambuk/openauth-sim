package io.openauth.sim.core.credentials.ocra;

import java.util.Locale;

/** Indicates the challenge question type defined in an OCRA suite. */
public enum OcraChallengeFormat {
    ALPHANUMERIC('A'),
    NUMERIC('N'),
    HEX('H'),
    CHARACTER('C');

    private final char token;

    OcraChallengeFormat(char token) {
        this.token = token;
    }

    public char token() {
        return token;
    }

    public static OcraChallengeFormat fromToken(char token) {
        char upper = Character.toUpperCase(token);
        for (OcraChallengeFormat value : values()) {
            if (value.token == upper) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported OCRA challenge format token: " + token + " (" + upper + ")");
    }

    public static OcraChallengeFormat fromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Challenge format token must not be blank");
        }
        return fromToken(token.toUpperCase(Locale.ROOT).charAt(0));
    }
}
