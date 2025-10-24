package io.openauth.sim.core.otp.hotp;

/** Supported HMAC algorithms for HOTP operations. */
public enum HotpHashAlgorithm {
    SHA1("HmacSHA1", 10),
    SHA256("HmacSHA256", 16),
    SHA512("HmacSHA512", 32);

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

    /** Canonical HMAC label used in verbose trace output. */
    public String traceLabel() {
        return switch (this) {
            case SHA1 -> "HMAC-SHA-1";
            case SHA256 -> "HMAC-SHA-256";
            case SHA512 -> "HMAC-SHA-512";
        };
    }
}
