package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Captures the crypto function tuple declared in an OCRA suite. */
public record OcraCryptoFunction(OcraHashAlgorithm hashAlgorithm, int responseDigits, Optional<Duration> timeStep)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OcraCryptoFunction {
        Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");
        Objects.requireNonNull(timeStep, "timeStep");
        if (responseDigits <= 0) {
            throw new IllegalArgumentException("Response digits must be positive");
        }
    }
}
