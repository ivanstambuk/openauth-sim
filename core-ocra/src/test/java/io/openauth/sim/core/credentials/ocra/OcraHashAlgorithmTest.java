package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraHashAlgorithmTest {

  @Test
  @DisplayName("fromToken normalises tokens regardless of prefix casing")
  void fromTokenNormalisesTokens() {
    assertEquals(OcraHashAlgorithm.SHA1, OcraHashAlgorithm.fromToken("sha1"));
    assertEquals(OcraHashAlgorithm.SHA512, OcraHashAlgorithm.fromToken("512"));
  }

  @Test
  @DisplayName("fromToken rejects blank values")
  void fromTokenRejectsBlankValues() {
    assertThrows(IllegalArgumentException.class, () -> OcraHashAlgorithm.fromToken("   "));
  }

  @Test
  @DisplayName("fromToken rejects unsupported algorithms")
  void fromTokenRejectsUnsupportedAlgorithms() {
    assertThrows(IllegalArgumentException.class, () -> OcraHashAlgorithm.fromToken("MD5"));
  }
}
