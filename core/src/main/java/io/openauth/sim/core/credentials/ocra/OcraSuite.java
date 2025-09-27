package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Immutable representation of an OCRA suite string. */
public record OcraSuite(String value, OcraCryptoFunction cryptoFunction, OcraDataInput dataInput)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public OcraSuite {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(cryptoFunction, "cryptoFunction");
    Objects.requireNonNull(dataInput, "dataInput");
  }
}
