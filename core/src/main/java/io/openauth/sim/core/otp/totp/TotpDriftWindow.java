package io.openauth.sim.core.otp.totp;

/** Defines acceptable backward/forward step drift during validation. */
public record TotpDriftWindow(int backwardSteps, int forwardSteps) {

  public TotpDriftWindow {
    if (backwardSteps < 0) {
      throw new IllegalArgumentException("backwardSteps must be non-negative");
    }
    if (forwardSteps < 0) {
      throw new IllegalArgumentException("forwardSteps must be non-negative");
    }
  }

  public static TotpDriftWindow of(int backwardSteps, int forwardSteps) {
    return new TotpDriftWindow(backwardSteps, forwardSteps);
  }
}
