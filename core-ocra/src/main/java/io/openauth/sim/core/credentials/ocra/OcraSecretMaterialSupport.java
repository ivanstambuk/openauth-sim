package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/** Utility functions for normalising OCRA secret inputs. */
final class OcraSecretMaterialSupport {

  private OcraSecretMaterialSupport() {
    // Utility class
  }

  static SecretMaterial normaliseSharedSecret(String value, SecretEncoding encoding) {
    Objects.requireNonNull(encoding, "encoding");
    if (value == null) {
      throw new IllegalArgumentException("sharedSecretKey must not be blank");
    }
    return switch (encoding) {
      case HEX -> SecretMaterial.fromHex(normaliseHex(value));
      case BASE64 -> normaliseBase64(value);
      case RAW -> normaliseRaw(value);
    };
  }

  static String normaliseHex(String value) {
    Objects.requireNonNull(value, "value");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Hex value must not be empty");
    }
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      trimmed = trimmed.substring(2);
    }
    String normalised = trimmed.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    if (normalised.isEmpty()) {
      throw new IllegalArgumentException("Hex value must not be empty");
    }
    if ((normalised.length() & 1) != 0) {
      throw new IllegalArgumentException("Hex value must contain an even number of characters");
    }
    if (!normalised.chars().allMatch(OcraSecretMaterialSupport::isHexCharacter)) {
      throw new IllegalArgumentException("Value must be hexadecimal");
    }
    return normalised;
  }

  private static SecretMaterial normaliseBase64(String value) {
    String cleaned = stripWhitespace(value);
    if (cleaned.isEmpty()) {
      throw new IllegalArgumentException("sharedSecretKey must not be blank");
    }
    try {
      Base64.getDecoder().decode(cleaned);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("sharedSecretKey must be valid Base64", ex);
    }
    return SecretMaterial.fromBase64(cleaned);
  }

  private static SecretMaterial normaliseRaw(String value) {
    if (value.isBlank()) {
      throw new IllegalArgumentException("sharedSecretKey must not be blank");
    }
    return SecretMaterial.fromBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String stripWhitespace(String value) {
    Objects.requireNonNull(value, "value");
    return value.replaceAll("\\s+", "");
  }

  private static boolean isHexCharacter(int ch) {
    return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
  }
}
