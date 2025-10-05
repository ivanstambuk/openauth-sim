package io.openauth.sim.core.otp.hotp;

/** Supported HMAC algorithms for HOTP operations. */
public enum HotpHashAlgorithm {
  SHA1("HmacSHA1", 10);

  private final String macAlgorithm;
  private final int minimumSecretLengthBytes;

  HotpHashAlgorithm(String macAlgorithm, int minimumSecretLengthBytes) {
    this.macAlgorithm = macAlgorithm;
    this.minimumSecretLengthBytes = minimumSecretLengthBytes;
  }

  /** Underlying JCA algorithm identifier. */
  public String macAlgorithm() {
    return macAlgorithm;
  }

  /** Minimum secret length recommended for the algorithm (bytes). */
  public int minimumSecretLengthBytes() {
    return minimumSecretLengthBytes;
  }
}
