package io.openauth.sim.core.otp.hotp;

import io.openauth.sim.core.model.SecretMaterial;
import java.util.Objects;

/** Descriptor for HOTP credential generation and validation. */
public record HotpDescriptor(String name, SecretMaterial secret, HotpHashAlgorithm algorithm, int digits) {

    private static final int MIN_DIGITS = 6;
    private static final int MAX_DIGITS = 8;

    public HotpDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(algorithm, "algorithm");

        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("descriptor name must not be blank");
        }

        if (digits < MIN_DIGITS || digits > MAX_DIGITS) {
            throw new IllegalArgumentException("digits must be between " + MIN_DIGITS + " and " + MAX_DIGITS);
        }
    }

    public static HotpDescriptor create(String name, SecretMaterial secret, HotpHashAlgorithm algorithm, int digits) {
        return new HotpDescriptor(name, secret, algorithm, digits);
    }
}
