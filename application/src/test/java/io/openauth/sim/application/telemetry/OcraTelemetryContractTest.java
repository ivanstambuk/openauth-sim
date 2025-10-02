package io.openauth.sim.application.telemetry;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("telemetry")
final class OcraTelemetryContractTest {

  @Test
  void ocraEvaluationSuccessFrameIncludesSanitizedFlag() {
    OcraTelemetryAdapter adapter = TelemetryContracts.ocraEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.evaluationSuccessFields();
    TelemetryFrame frame = adapter.success(TelemetryContractTestSupport.telemetryId(), fields);

    TelemetryContractTestSupport.assertEvaluationSuccessFrame(frame);
  }

  @Test
  void ocraEvaluationValidationFramePropagatesSanitizedFlag() {
    OcraTelemetryAdapter adapter = TelemetryContracts.ocraEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.evaluationValidationFields();
    TelemetryFrame frame =
        adapter.validationFailure(
            TelemetryContractTestSupport.telemetryId(),
            "validation_error",
            "suite is required",
            true,
            fields);

    TelemetryContractTestSupport.assertEvaluationValidationFrame(frame, true);
  }

  @Test
  void ocraEvaluationErrorFrameDefaultsToUnsanitized() {
    OcraTelemetryAdapter adapter = TelemetryContracts.ocraEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.evaluationErrorFields();
    TelemetryFrame frame =
        adapter.error(
            TelemetryContractTestSupport.telemetryId(),
            "unexpected_error",
            "Unexpected failure during evaluation",
            false,
            fields);

    TelemetryContractTestSupport.assertEvaluationErrorFrame(frame);
  }
}
