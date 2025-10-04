package io.openauth.sim.application.telemetry;

/** Factory for shared telemetry adapters. */
public final class TelemetryContracts {

  private static final OcraTelemetryAdapter OCRA_EVALUATION_ADAPTER =
      new OcraTelemetryAdapter("ocra.evaluate");

  private static final OcraTelemetryAdapter OCRA_VERIFICATION_ADAPTER =
      new OcraTelemetryAdapter("ocra.verify");

  private static final OcraTelemetryAdapter OCRA_SEEDING_ADAPTER =
      new OcraTelemetryAdapter("ocra.seed");

  private TelemetryContracts() {
    throw new AssertionError("No instances");
  }

  /** Returns the shared adapter for OCRA evaluation telemetry. */
  public static OcraTelemetryAdapter ocraEvaluationAdapter() {
    return OCRA_EVALUATION_ADAPTER;
  }

  /** Returns the shared adapter for OCRA verification telemetry. */
  public static OcraTelemetryAdapter ocraVerificationAdapter() {
    return OCRA_VERIFICATION_ADAPTER;
  }

  /** Returns the shared adapter for OCRA credential seeding telemetry. */
  public static OcraTelemetryAdapter ocraSeedingAdapter() {
    return OCRA_SEEDING_ADAPTER;
  }
}
