package io.openauth.sim.application.telemetry;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("telemetry")
final class HotpTelemetryContractTest {

  @Test
  void hotpEvaluationSuccessFrameIncludesSanitizedFlag() {
    HotpTelemetryAdapter adapter = TelemetryContracts.hotpEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.hotpEvaluationSuccessFields();
    TelemetryFrame frame = adapter.success(TelemetryContractTestSupport.telemetryId(), fields);

    TelemetryContractTestSupport.assertHotpEvaluationSuccessFrame(frame);
  }

  @Test
  void hotpEvaluationValidationFramePropagatesSanitizedFlag() {
    HotpTelemetryAdapter adapter = TelemetryContracts.hotpEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.hotpEvaluationValidationFields();
    TelemetryFrame frame =
        adapter.validationFailure(
            TelemetryContractTestSupport.telemetryId(),
            "otp_mismatch",
            "OTP mismatch",
            true,
            fields);

    TelemetryContractTestSupport.assertHotpEvaluationValidationFrame(frame, true);
  }

  @Test
  void hotpEvaluationErrorFrameDefaultsToUnsanitized() {
    HotpTelemetryAdapter adapter = TelemetryContracts.hotpEvaluationAdapter();

    Map<String, Object> fields = TelemetryContractTestSupport.hotpEvaluationErrorFields();
    TelemetryFrame frame =
        adapter.error(
            TelemetryContractTestSupport.telemetryId(),
            "unexpected_error",
            "Unexpected failure during HOTP evaluation",
            false,
            fields);

    TelemetryContractTestSupport.assertHotpEvaluationErrorFrame(frame);
  }

  @Test
  void hotpIssuanceAdapterProducesIssuedFrame() {
    HotpTelemetryAdapter adapter = TelemetryContracts.hotpIssuanceAdapter();

    TelemetryFrame frame =
        adapter.status(
            "issued",
            TelemetryContractTestSupport.telemetryId(),
            "issued",
            true,
            null,
            TelemetryContractTestSupport.hotpIssuanceSuccessFields());

    TelemetryContractTestSupport.assertHotpIssuanceSuccessFrame(frame);
  }
}
