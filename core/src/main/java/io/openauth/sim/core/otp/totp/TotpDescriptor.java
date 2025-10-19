package io.openauth.sim.core.otp.totp;

import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.util.Objects;

/** Descriptor for TOTP credential generation and validation. */
public record TotpDescriptor(
        String name,
        SecretMaterial secret,
        TotpHashAlgorithm algorithm,
        int digits,
        Duration stepDuration,
        TotpDriftWindow driftWindow) {

    private static final int MIN_DIGITS = 6;
    private static final int MAX_DIGITS = 8;

    public TotpDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(stepDuration, "stepDuration");
        Objects.requireNonNull(driftWindow, "driftWindow");

        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("descriptor name must not be blank");
        }

        if (digits < MIN_DIGITS || digits > MAX_DIGITS) {
            throw new IllegalArgumentException("digits must be between " + MIN_DIGITS + " and " + MAX_DIGITS);
        }

        if (stepDuration.isZero() || stepDuration.isNegative()) {
            throw new IllegalArgumentException("step duration must be positive");
        }

        if (stepDuration.toNanos() % 1_000_000_000L != 0) {
            throw new IllegalArgumentException("step duration must be an integer number of seconds");
        }
    }

    public static TotpDescriptor create(
            String name, SecretMaterial secret, TotpHashAlgorithm algorithm, int digits, Duration stepDuration) {
        return create(name, secret, algorithm, digits, stepDuration, TotpDriftWindow.of(1, 1));
    }

    public static TotpDescriptor create(
            String name,
            SecretMaterial secret,
            TotpHashAlgorithm algorithm,
            int digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow) {
        return new TotpDescriptor(name, secret, algorithm, digits, stepDuration, driftWindow);
    }

    /** Convenience accessor for the configured time step in seconds. */
    public long stepSeconds() {
        return stepDuration.getSeconds();
    }
}
