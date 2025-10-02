package io.openauth.sim.core.credentials.ocra;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Parsed representation of a challenge question descriptor. */
public record OcraChallengeQuestion(OcraChallengeFormat format, int length)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public OcraChallengeQuestion {
    Objects.requireNonNull(format, "format");
    if (length <= 0) {
      throw new IllegalArgumentException("Challenge length must be positive");
    }
  }
}
