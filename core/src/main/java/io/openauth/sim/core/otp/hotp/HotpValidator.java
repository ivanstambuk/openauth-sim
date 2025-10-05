package io.openauth.sim.core.otp.hotp;

import java.util.Objects;

/** Validates HOTP codes against a descriptor and counter. */
public final class HotpValidator {

  private HotpValidator() {
    throw new AssertionError("No instances");
  }

  public static HotpVerificationResult verify(
      HotpDescriptor descriptor, long counter, String candidateOtp) {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(candidateOtp, "candidateOtp");

    if (counter < 0) {
      throw new IllegalArgumentException("counter must be non-negative");
    }
    if (counter == Long.MAX_VALUE) {
      throw new IllegalStateException("Counter overflow");
    }

    String normalized = candidateOtp.trim();
    if (normalized.length() != descriptor.digits()
        || !normalized.chars().allMatch(Character::isDigit)) {
      return HotpVerificationResult.failure(counter);
    }

    String expected = HotpGenerator.generate(descriptor, counter);
    if (expected.equals(normalized)) {
      return HotpVerificationResult.success(counter + 1);
    }
    return HotpVerificationResult.failure(counter);
  }
}
