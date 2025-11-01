package io.openauth.sim.rest.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.encoding.Base32SecretCodec;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.rest.EvaluationWindowRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class TotpEvaluationServiceTest {

    private TotpEvaluationApplicationService applicationService;
    private TotpEvaluationService service;

    @BeforeEach
    void setUp() {
        applicationService = Mockito.mock(TotpEvaluationApplicationService.class);
        service = new TotpEvaluationService(applicationService);
    }

    @Test
    @DisplayName("Inline evaluation accepts Base32 shared secrets")
    void inlineEvaluationAcceptsBase32Secrets() {
        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("credentialSource", "inline");
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "generated", null, true, telemetryFields);
        List<OtpPreview> previews = List.of(new OtpPreview("000000", 0, "654321"));
        EvaluationResult result = new EvaluationResult(
                signal,
                false,
                null,
                true,
                0,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1),
                "654321",
                previews,
                null);
        when(applicationService.evaluate(any(EvaluationCommand.Inline.class), anyBoolean()))
                .thenReturn(result);

        String base32 = "MFRGGZDFMZTWQ2LK";
        TotpInlineEvaluationRequest request = new TotpInlineEvaluationRequest(
                null,
                base32,
                "SHA1",
                6,
                30L,
                new EvaluationWindowRequest(1, 1),
                null,
                null,
                "",
                Map.of(),
                Boolean.FALSE);

        TotpEvaluationResponse response = service.evaluateInline(request);

        assertEquals("generated", response.status());
        assertEquals("inline", response.metadata().credentialSource());
        assertEquals(1, response.previews().size());
        assertEquals(0, response.previews().get(0).delta());
        assertEquals("000000", response.previews().get(0).counter());
        assertEquals("654321", response.previews().get(0).otp());

        ArgumentCaptor<EvaluationCommand.Inline> captor = ArgumentCaptor.forClass(EvaluationCommand.Inline.class);
        verify(applicationService).evaluate(captor.capture(), anyBoolean());
        assertEquals(Base32SecretCodec.toUpperHex(base32), captor.getValue().sharedSecretHex());
    }

    @Test
    @DisplayName("Inline evaluation rejects invalid Base32 secrets")
    void inlineEvaluationRejectsInvalidBase32Secrets() {
        TotpInlineEvaluationRequest request = new TotpInlineEvaluationRequest(
                null,
                "!!!!",
                "SHA1",
                6,
                30L,
                new EvaluationWindowRequest(1, 1),
                null,
                null,
                "",
                Map.of(),
                Boolean.FALSE);

        TotpEvaluationValidationException exception =
                assertThrows(TotpEvaluationValidationException.class, () -> service.evaluateInline(request));

        assertEquals("shared_secret_base32_invalid", exception.reasonCode());
        assertEquals("sharedSecretBase32", exception.details().get("field"));
    }
}
