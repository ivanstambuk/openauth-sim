package io.openauth.sim.application.ocra;

import java.util.Locale;
import java.util.Objects;

/** Deterministic helper for generating shared inline credential identifiers across facades. */
public final class OcraInlineIdentifiers {

  private static final String PREFIX = "ocra-inline-";

  private OcraInlineIdentifiers() {}

  public static String sharedIdentifier(String suite, String sharedSecretHex) {
    Objects.requireNonNull(suite, "suite");
    Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");

    String normalizedSuite = suite.trim();
    String normalizedSecret = sharedSecretHex.replace(" ", "").trim().toUpperCase(Locale.ROOT);
    if (normalizedSuite.isEmpty() || normalizedSecret.isEmpty()) {
      throw new IllegalArgumentException("suite and sharedSecretHex must be provided");
    }
    int hash = Objects.hash(normalizedSuite, normalizedSecret);
    return PREFIX + Integer.toHexString(hash);
  }
}
