package io.openauth.sim.core.emv.cap;

import java.util.Locale;
import java.util.Objects;

/** Supported EMV/CAP customer interaction modes. */
public enum EmvCapMode {
    IDENTIFY,
    RESPOND,
    SIGN;

    /** Map a case-insensitive label to a mode value. */
    public static EmvCapMode fromLabel(String label) {
        Objects.requireNonNull(label, "label");
        return valueOf(label.trim().toUpperCase(Locale.ROOT));
    }

    /** Validate that the supplied customer inputs align with the mode's expectations. */
    public void validateCustomerInputs(EmvCapInput.CustomerInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        switch (this) {
            case IDENTIFY -> {
                ensureEmpty(inputs.challenge(), "Identify mode does not accept a challenge input");
                ensureEmpty(inputs.reference(), "Identify mode does not accept a reference input");
                ensureEmpty(inputs.amount(), "Identify mode does not accept an amount input");
            }
            case RESPOND -> {
                ensurePresent(inputs.challenge(), "Respond mode requires a numeric challenge");
                ensureEmpty(inputs.reference(), "Respond mode does not accept a reference input");
                ensureEmpty(inputs.amount(), "Respond mode does not accept an amount input");
            }
            case SIGN -> {
                ensurePresent(inputs.challenge(), "Sign mode requires a numeric challenge");
                ensurePresent(inputs.reference(), "Sign mode requires a reference value");
                ensurePresent(inputs.amount(), "Sign mode requires an amount value");
            }
        }
    }

    private static void ensurePresent(String value, String message) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void ensureEmpty(String value, String message) {
        if (!value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
