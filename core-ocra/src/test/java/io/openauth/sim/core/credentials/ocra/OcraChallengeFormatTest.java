package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraChallengeFormatTest {

    @Test
    @DisplayName("fromToken resolves valid tokens and rejects invalid ones")
    void fromTokenResolvesTokens() {
        assertEquals(OcraChallengeFormat.ALPHANUMERIC, OcraChallengeFormat.fromToken('a'));
        assertEquals(OcraChallengeFormat.NUMERIC, OcraChallengeFormat.fromToken('N'));
        assertEquals(OcraChallengeFormat.HEX, OcraChallengeFormat.fromToken('h'));
        assertEquals(OcraChallengeFormat.CHARACTER, OcraChallengeFormat.fromToken('c'));
        assertThrows(IllegalArgumentException.class, () -> OcraChallengeFormat.fromToken('X'));
    }

    @Test
    @DisplayName("fromToken string rejects blank input")
    void fromTokenStringRejectsBlank() {
        assertEquals(OcraChallengeFormat.HEX, OcraChallengeFormat.fromToken("h"));
        assertThrows(IllegalArgumentException.class, () -> OcraChallengeFormat.fromToken("   "));
        assertThrows(IllegalArgumentException.class, () -> OcraChallengeFormat.fromToken((String) null));
    }
}
