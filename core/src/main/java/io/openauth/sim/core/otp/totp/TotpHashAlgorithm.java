package io.openauth.sim.core.otp.totp;

/** Supported HMAC algorithms for TOTP operations. */
public enum TotpHashAlgorithm {
    SHA1("HmacSHA1", 10),
    SHA256("HmacSHA256", 16),
    SHA512("HmacSHA512", 32);

    private final String macAlgorithm;
    private final int minimumSecretLengthBytes;

    TotpHashAlgorithm(String macAlgorithm, int minimumSecretLengthBytes) {
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
