package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.SecretEncoding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraSecretMaterialSupportTest {

  @Test
  @DisplayName("normaliseSharedSecret trims and decodes supported encodings")
  void normaliseSharedSecretDecodesEncodings() {
    assertEquals(
        "31323334",
        OcraSecretMaterialSupport.normaliseSharedSecret("0x31323334", SecretEncoding.HEX).asHex());

    assertEquals(
        "31323334",
        OcraSecretMaterialSupport.normaliseSharedSecret(" MTIzNA== ", SecretEncoding.BASE64)
            .asHex());

    assertEquals(
        "31323334",
        OcraSecretMaterialSupport.normaliseSharedSecret("1234", SecretEncoding.RAW).asHex());
  }

  @Test
  @DisplayName("normaliseSharedSecret rejects blank inputs")
  void normaliseSharedSecretRejectsBlank() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OcraSecretMaterialSupport.normaliseSharedSecret(null, SecretEncoding.HEX));
    assertThrows(
        IllegalArgumentException.class,
        () -> OcraSecretMaterialSupport.normaliseSharedSecret("   ", SecretEncoding.RAW));
  }

  @Test
  @DisplayName("normaliseHex enforces even length and valid characters")
  void normaliseHexValidatesInput() {
    assertThrows(
        IllegalArgumentException.class, () -> OcraSecretMaterialSupport.normaliseHex("0x1"));
    assertThrows(
        IllegalArgumentException.class, () -> OcraSecretMaterialSupport.normaliseHex("0xGG"));
  }

  @Test
  @DisplayName("normaliseBase64 rejects invalid values")
  void normaliseBase64RejectsInvalid() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OcraSecretMaterialSupport.normaliseSharedSecret("@@@", SecretEncoding.BASE64));
  }
}
