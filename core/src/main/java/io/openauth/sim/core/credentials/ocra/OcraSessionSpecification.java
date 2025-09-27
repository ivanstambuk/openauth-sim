package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Session information hashing declaration in an OCRA suite. */
public record OcraSessionSpecification(OcraHashAlgorithm hashAlgorithm) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public OcraSessionSpecification {
    Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");
  }
}
