package io.openauth.sim.core.otp.hotp;

/** Result of HOTP validation with optional counter advancement. */
public record HotpVerificationResult(boolean valid, long nextCounter) {

    public static HotpVerificationResult success(long nextCounter) {
        return new HotpVerificationResult(true, nextCounter);
    }

    public static HotpVerificationResult failure(long currentCounter) {
        return new HotpVerificationResult(false, currentCounter);
    }
}
