package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

/** Timestamp input declaration for time-based OCRA suites. */
public record OcraTimestampSpecification(Duration step) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OcraTimestampSpecification {
        Objects.requireNonNull(step, "step");
        if (step.isNegative() || step.isZero()) {
            throw new IllegalArgumentException("Timestamp step must be positive");
        }
    }
}
