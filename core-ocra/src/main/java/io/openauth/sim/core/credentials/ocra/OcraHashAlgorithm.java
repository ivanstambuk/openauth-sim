package io.openauth.sim.core.credentials.ocra;

import java.util.Locale;

/** Supported hash algorithms within OCRA suite definitions. */
public enum OcraHashAlgorithm {
  SHA1("SHA1"),
  SHA256("SHA256"),
  SHA512("SHA512");

  private final String token;

  OcraHashAlgorithm(String token) {
    this.token = token;
  }

  public String token() {
    return token;
  }

  /** Expected digest length in bytes. */
  public int digestLengthBytes() {
    return switch (this) {
      case SHA1 -> 20;
      case SHA256 -> 32;
      case SHA512 -> 64;
    };
  }

  /** Expected hexadecimal character count for the digest. */
  public int hexLength() {
    return digestLengthBytes() * 2;
  }

  public static OcraHashAlgorithm fromToken(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Hash algorithm token must not be blank");
    }
    String normalised = token.trim().toUpperCase(Locale.ROOT);
    if (!normalised.startsWith("SHA")) {
      int hIndex = normalised.indexOf('H');
      int start = hIndex >= 0 ? hIndex + 1 : 0;
      normalised = "SHA" + normalised.substring(start);
    }
    for (OcraHashAlgorithm value : values()) {
      if (value.token.equals(normalised)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported OCRA hash algorithm: " + token);
  }
}
