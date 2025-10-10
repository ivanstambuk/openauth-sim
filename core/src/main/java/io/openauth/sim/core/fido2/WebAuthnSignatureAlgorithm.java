package io.openauth.sim.core.fido2;

import java.util.Arrays;
import java.util.Locale;

/** Supported WebAuthn signature algorithms backed by COSE algorithm identifiers. */
public enum WebAuthnSignatureAlgorithm {
  ES256("ES256", -7),
  ES384("ES384", -35),
  ES512("ES512", -36),
  RS256("RS256", -257),
  PS256("PS256", -37),
  EDDSA("EdDSA", -8);

  private final String label;
  private final int coseIdentifier;

  WebAuthnSignatureAlgorithm(String label, int coseIdentifier) {
    this.label = label;
    this.coseIdentifier = coseIdentifier;
  }

  /** Human-friendly label matching WebAuthn registration metadata. */
  public String label() {
    return label;
  }

  /** COSE algorithm identifier from the WebAuthn specification. */
  public int coseIdentifier() {
    return coseIdentifier;
  }

  /**
   * Resolve an algorithm by its human-readable label.
   *
   * @throws IllegalArgumentException when the label is unknown
   */
  public static WebAuthnSignatureAlgorithm fromLabel(String label) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("Algorithm label must not be blank");
    }
    String normalized = label.trim().toUpperCase(Locale.US);
    return Arrays.stream(values())
        .filter(algorithm -> algorithm.label.toUpperCase(Locale.US).equals(normalized))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported FIDO2 algorithm: " + label));
  }

  /**
   * Resolve an algorithm by its COSE identifier.
   *
   * @throws IllegalArgumentException when the identifier is unknown
   */
  public static WebAuthnSignatureAlgorithm fromCoseIdentifier(int coseIdentifier) {
    return Arrays.stream(values())
        .filter(algorithm -> algorithm.coseIdentifier == coseIdentifier)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported FIDO2 algorithm identifier: " + coseIdentifier));
  }
}
