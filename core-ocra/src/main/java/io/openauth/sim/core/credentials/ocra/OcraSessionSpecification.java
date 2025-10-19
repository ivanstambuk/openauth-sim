package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;

/** Session information declaration in an OCRA suite. */
public record OcraSessionSpecification(int lengthBytes) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OcraSessionSpecification {
        if (lengthBytes <= 0) {
            throw new IllegalArgumentException("Session length must be positive");
        }
    }
}
