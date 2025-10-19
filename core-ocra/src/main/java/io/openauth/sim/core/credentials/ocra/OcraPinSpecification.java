package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Represents the PIN hashing strategy declared in an OCRA suite. */
public record OcraPinSpecification(OcraHashAlgorithm hashAlgorithm) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public OcraPinSpecification {
        Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");
    }
}
