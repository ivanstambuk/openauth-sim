package io.openauth.sim.core.otp.totp;

/** Result of TOTP validation indicating success and any matched skew steps. */
public record TotpVerificationResult(boolean valid, int matchedSkewSteps) {

  private static final int FAILURE_SKEW_SENTINEL = Integer.MIN_VALUE;

  public static TotpVerificationResult success(int matchedSkewSteps) {
    return new TotpVerificationResult(true, matchedSkewSteps);
  }

  public static TotpVerificationResult failure() {
    return new TotpVerificationResult(false, FAILURE_SKEW_SENTINEL);
  }
}
