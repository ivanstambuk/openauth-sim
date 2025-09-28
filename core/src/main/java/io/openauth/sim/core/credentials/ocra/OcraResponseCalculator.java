package io.openauth.sim.core.credentials.ocra;

import java.util.Objects;

/** Placeholder execution helper for OCRA response calculation. */
public final class OcraResponseCalculator {

  static final String UNIMPLEMENTED_MESSAGE =
      "OCRA response calculation helper not yet implemented";

  private OcraResponseCalculator() {
    // Utility class
  }

  public static String generate(OcraCredentialDescriptor descriptor, OcraExecutionContext context) {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(context, "context");
    throw new UnsupportedOperationException(UNIMPLEMENTED_MESSAGE);
  }

  /**
   * Placeholder execution context capturing the runtime inputs needed to evaluate an OCRA response.
   */
  public record OcraExecutionContext(
      Long counter,
      String question,
      String sessionInformation,
      String clientChallenge,
      String serverChallenge,
      String pinHashHex,
      String timestampHex) {

    public OcraExecutionContext {
      // no-op – values are optional placeholders; validation added when helper lands.
    }
  }
}
