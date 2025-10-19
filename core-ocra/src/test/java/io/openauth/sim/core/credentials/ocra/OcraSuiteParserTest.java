package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OcraSuiteParserTest {

    @Test
    @DisplayName("parse supports suites with counter, session hash, and timestamp")
    void parseComplexSuite() {
        OcraSuite suite = OcraSuiteParser.parse("OCRA-1:HOTP-T30-SHA256-8:C-QN08-PSHA1-SSH512-T32");

        assertEquals("OCRA-1:HOTP-T30-SHA256-8:C-QN08-PSHA1-SSH512-T32", suite.value());
        OcraCryptoFunction crypto = suite.cryptoFunction();
        OcraDataInput dataInput = suite.dataInput();

        assertEquals(OcraHashAlgorithm.SHA256, crypto.hashAlgorithm());
        assertEquals(8, crypto.responseDigits());
        assertEquals(Duration.ofSeconds(30), crypto.timeStep().orElseThrow());

        assertEquals(true, dataInput.counter());
        assertEquals(8, dataInput.challengeQuestion().orElseThrow().length());
        assertEquals(OcraHashAlgorithm.SHA1, dataInput.pin().orElseThrow().hashAlgorithm());
        assertEquals(
                OcraHashAlgorithm.SHA512.digestLengthBytes(),
                dataInput.sessionInformation().orElseThrow().lengthBytes());
        assertEquals(Duration.ofSeconds(32), dataInput.timestamp().orElseThrow().step());
    }

    @Test
    @DisplayName("blank suite is rejected")
    void blankSuiteRejected() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("   "));
    }

    @Test
    @DisplayName("invalid prefix is rejected")
    void invalidPrefixRejected() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-2:HOTP-SHA1-6:QN08"));
    }

    @Test
    @DisplayName("challenge token must use numeric length")
    void challengeTokenMustBeNumeric() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA0X"));
    }

    @Test
    @DisplayName("time-step indicator requires digits")
    void timeStepRequiresDigits() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-T-SHA1-6:QN08"));
    }

    @Test
    @DisplayName("timestamp token requires digits")
    void timestampTokenRequiresDigits() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QN08-TS"));
    }

    @Test
    @DisplayName("crypto function segment must not be blank")
    void cryptoFunctionMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:   :QA08"));
    }

    @Test
    @DisplayName("crypto function must begin with HOTP prefix")
    void cryptoFunctionRequiresHOTP() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:OTP-SHA1-6:QA08"));
    }

    @Test
    @DisplayName("SHA token must be present after HOTP prefix")
    void shaTokenRequired() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-123:QA08"));
    }

    @Test
    @DisplayName("hash algorithm digits are required")
    void hashDigitsRequired() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA-6:QA08"));
    }

    @Test
    @DisplayName("response digit token must be numeric")
    void responseDigitsMustBeNumeric() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-X:QA08"));
    }

    @Test
    @DisplayName("challenge token must include format length")
    void challengeTokenRequiresFormatLength() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:Q"));
    }

    @Test
    @DisplayName("missing data input segment is rejected")
    void dataInputSegmentRequired() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6"));
    }

    @Test
    @DisplayName("data input ignores empty tokens between separators")
    void dataInputIgnoresEmptyTokens() {
        OcraSuite suite = OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:C--QA08");
        assertEquals(true, suite.dataInput().counter());
        assertEquals(8, suite.dataInput().challengeQuestion().orElseThrow().length());
    }

    @Test
    @DisplayName("session length tokens resolve defaults, explicit digits, and hash aliases")
    void sessionTokensResolveLengths() {
        OcraSuite defaultSession = OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA08-S");
        assertEquals(
                64,
                defaultSession.dataInput().sessionInformation().orElseThrow().lengthBytes());

        OcraSuite numericSession = OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA08-S128");
        assertEquals(
                128,
                numericSession.dataInput().sessionInformation().orElseThrow().lengthBytes());

        OcraSuite hashSession = OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA08-SSHA256");
        assertEquals(
                OcraHashAlgorithm.SHA256.digestLengthBytes(),
                hashSession.dataInput().sessionInformation().orElseThrow().lengthBytes());
    }

    @Test
    @DisplayName("timestamp specification falls back to crypto time-step when omitted from data input")
    void timestampFallsBackToCryptoTimeStep() {
        OcraSuite suite = OcraSuiteParser.parse("OCRA-1:HOTP-T90-SHA1-6:QA08");
        assertEquals(Duration.ofSeconds(90), suite.cryptoFunction().timeStep().orElseThrow());
        assertEquals(
                Duration.ofSeconds(90),
                suite.dataInput().timestamp().orElseThrow().step());
    }

    @Test
    @DisplayName("duplicate PIN declarations are rejected")
    void duplicatePinDeclarationsRejected() {
        assertThrows(
                IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA08-PSHA1-PSHA256"));
    }

    @Test
    @DisplayName("unsupported data input token triggers descriptive error")
    void unsupportedDataInputTokenRejected() {
        assertThrows(IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA08-X99"));
    }
}
