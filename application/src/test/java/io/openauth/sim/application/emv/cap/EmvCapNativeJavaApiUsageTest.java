package io.openauth.sim.application.emv.cap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import org.junit.jupiter.api.Test;

final class EmvCapNativeJavaApiUsageTest {

    @Test
    void inlineIdentifyEvaluationProducesOtpAndPreviews() {
        EvaluationRequest request = new EvaluationRequest(
                EmvCapMode.IDENTIFY,
                "00112233445566778899AABBCCDDEEFF", // synthetic master key (hex)
                "00B4",
                4,
                8,
                0,
                0,
                "0000000000000000",
                "0000000000000000000000000000000000000000",
                "00001F0000000000000000000000000000000000",
                new EmvCapEvaluationApplicationService.CustomerInputs("1234", "5678", "00845"),
                EmvCapEvaluationApplicationService.TransactionData.empty(),
                "1000A000000000000000",
                "06770A03A48000");

        EmvCapEvaluationApplicationService service = new EmvCapEvaluationApplicationService();

        EvaluationResult result = service.evaluate(request);

        assertNotNull(result.telemetry());
    }
}
