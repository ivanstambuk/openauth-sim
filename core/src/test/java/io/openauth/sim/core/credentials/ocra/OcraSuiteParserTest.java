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
    assertThrows(
        IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-2:HOTP-SHA1-6:QN08"));
  }

  @Test
  @DisplayName("challenge token must use numeric length")
  void challengeTokenMustBeNumeric() {
    assertThrows(
        IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QA0X"));
  }

  @Test
  @DisplayName("time-step indicator requires digits")
  void timeStepRequiresDigits() {
    assertThrows(
        IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-T-SHA1-6:QN08"));
  }

  @Test
  @DisplayName("timestamp token requires digits")
  void timestampTokenRequiresDigits() {
    assertThrows(
        IllegalArgumentException.class, () -> OcraSuiteParser.parse("OCRA-1:HOTP-SHA1-6:QN08-TS"));
  }
}
