package io.openauth.sim.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("telemetry")
final class OcraTelemetryContractTest {

  private static final String CONTRACTS_CLASS =
      "io.openauth.sim.application.telemetry.TelemetryContracts";

  @Test
  void ocraEvaluationSuccessFrameIncludesSanitizedFlag() throws Exception {
    Object adapter = ocraEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.evaluationSuccessFields();
    Object frame =
        adapter
            .getClass()
            .getMethod("success", String.class, Map.class)
            .invoke(adapter, TelemetryContractTestSupport.telemetryId(), fields);

    TelemetryContractTestSupport.assertEvaluationSuccessFrame(frame);
  }

  @Test
  void ocraEvaluationValidationFramePropagatesSanitizedFlag() throws Exception {
    Object adapter = ocraEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.evaluationValidationFields();
    Object frame =
        adapter
            .getClass()
            .getMethod(
                "validationFailure",
                String.class,
                String.class,
                String.class,
                boolean.class,
                Map.class)
            .invoke(
                adapter,
                TelemetryContractTestSupport.telemetryId(),
                "validation_error",
                "suite is required",
                true,
                fields);

    TelemetryContractTestSupport.assertEvaluationValidationFrame(frame, true);
  }

  @Test
  void ocraEvaluationErrorFrameDefaultsToUnsanitized() throws Exception {
    Object adapter = ocraEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.evaluationErrorFields();
    Object frame =
        adapter
            .getClass()
            .getMethod("error", String.class, String.class, String.class, boolean.class, Map.class)
            .invoke(
                adapter,
                TelemetryContractTestSupport.telemetryId(),
                "unexpected_error",
                "Unexpected failure during evaluation",
                false,
                fields);

    TelemetryContractTestSupport.assertEvaluationErrorFrame(frame);
  }

  private static Object ocraEvaluationAdapter() throws Exception {
    Class<?> contracts = Class.forName(CONTRACTS_CLASS);
    Object adapter = contracts.getMethod("ocraEvaluationAdapter").invoke(null);
    assertNotNull(adapter, "Expected a shared OCRA telemetry adapter instance");
    assertDoesNotThrow(() -> adapter.getClass().getMethod("success", String.class, Map.class));
    return adapter;
  }
}
