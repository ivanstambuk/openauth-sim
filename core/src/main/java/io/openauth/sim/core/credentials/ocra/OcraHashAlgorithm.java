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
