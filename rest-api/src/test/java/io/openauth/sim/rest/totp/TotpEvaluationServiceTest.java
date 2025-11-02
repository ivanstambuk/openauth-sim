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
import java.time.Instant;
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
                new EvaluationWindowRequest(null, null),
                null,
                null,
                "",
                metadata(),
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
        assertEquals("stored:baseline", telemetryFields.get("samplePresetKey"));
        assertEquals("Baseline", telemetryFields.get("samplePresetLabel"));
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

    @Test
    @DisplayName("Stored evaluation propagates telemetry and metadata")
    void storedEvaluationReturnsResponse() {
        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("credentialSource", "stored");
        telemetryFields.put("digits", 8);
        telemetryFields.put("telemetryId", "rest-totp-1234");
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "match", "match", true, telemetryFields);
        List<OtpPreview> previews =
                List.of(new OtpPreview("000001", -1, "123456"), new OtpPreview("000002", 0, "654321"));
        EvaluationResult result = new EvaluationResult(
                signal,
                true,
                "totp:baseline",
                true,
                0,
                TotpHashAlgorithm.SHA256,
                8,
                Duration.ofSeconds(45),
                TotpDriftWindow.of(2, 3),
                "654321",
                previews,
                null);
        when(applicationService.evaluate(any(EvaluationCommand.Stored.class), anyBoolean()))
                .thenReturn(result);

        TotpStoredEvaluationRequest request = new TotpStoredEvaluationRequest(
                " totp:baseline ",
                "654321",
                1_700_000_000L,
                new EvaluationWindowRequest(2, 3),
                1_700_000_015L,
                Boolean.TRUE);

        TotpEvaluationResponse response = service.evaluateStored(request);

        assertEquals("match", response.status());
        assertEquals("654321", response.otp());
        assertEquals(2, response.previews().size());
        assertEquals("stored", response.metadata().credentialSource());
        assertEquals(0, response.metadata().matchedSkewSteps());
        assertEquals(8, response.metadata().digits());
        assertEquals(45L, response.metadata().stepSeconds());

        ArgumentCaptor<EvaluationCommand.Stored> captor = ArgumentCaptor.forClass(EvaluationCommand.Stored.class);
        verify(applicationService).evaluate(captor.capture(), anyBoolean());
        EvaluationCommand.Stored command = captor.getValue();
        assertEquals("totp:baseline", command.credentialId());
        assertEquals("654321", command.otp());
        assertEquals(2, command.driftWindow().backwardSteps());
        assertEquals(3, command.driftWindow().forwardSteps());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), command.evaluationInstant());
        assertEquals(
                Instant.ofEpochSecond(1_700_000_015L),
                command.timestampOverride().orElseThrow());
    }

    @Test
    @DisplayName("Inline evaluation surfaces validation failures")
    void inlineEvaluationPropagatesValidation() {
        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("credentialSource", "inline");
        telemetryFields.put("driftBackwardSteps", 2);
        telemetryFields.put("driftForwardSteps", 1);
        TelemetrySignal signal =
                new TelemetrySignal(TelemetryStatus.INVALID, "otp_mismatch", "otp mismatch", true, telemetryFields);
        EvaluationResult result = new EvaluationResult(
                signal,
                false,
                null,
                false,
                -1,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(2, 1),
                "",
                List.of(),
                null);
        when(applicationService.evaluate(any(EvaluationCommand.Inline.class), anyBoolean()))
                .thenReturn(result);

        TotpInlineEvaluationRequest request = new TotpInlineEvaluationRequest(
                "0011223344",
                null,
                "SHA1",
                6,
                30L,
                new EvaluationWindowRequest(2, 1),
                null,
                null,
                "",
                Map.of(),
                Boolean.FALSE);

        TotpEvaluationValidationException exception =
                assertThrows(TotpEvaluationValidationException.class, () -> service.evaluateInline(request));

        assertEquals("otp_mismatch", exception.reasonCode());
        assertEquals(-1, exception.details().get("matchedSkewSteps"));
    }

    @Test
    @DisplayName("Stored evaluation surfaces unexpected errors")
    void storedEvaluationPropagatesUnexpected() {
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.ERROR, "error", "failure", false, Map.of());
        EvaluationResult result = new EvaluationResult(
                signal,
                true,
                "totp:baseline",
                false,
                0,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1),
                "",
                List.of(),
                null);
        when(applicationService.evaluate(any(EvaluationCommand.Stored.class), anyBoolean()))
                .thenReturn(result);

        TotpStoredEvaluationRequest request =
                new TotpStoredEvaluationRequest("totp:baseline", "", null, null, null, Boolean.FALSE);

        TotpEvaluationUnexpectedException exception =
                assertThrows(TotpEvaluationUnexpectedException.class, () -> service.evaluateStored(request));

        assertEquals("TOTP evaluation failed unexpectedly", exception.getMessage());
    }

    @Test
    @DisplayName("Stored evaluation requires credential ID")
    void storedEvaluationRequiresCredentialId() {
        TotpStoredEvaluationRequest request =
                new TotpStoredEvaluationRequest("   ", "123456", null, null, null, Boolean.FALSE);

        TotpEvaluationValidationException exception =
                assertThrows(TotpEvaluationValidationException.class, () -> service.evaluateStored(request));

        assertEquals("credential_id_required", exception.reasonCode());
    }

    private static Map<String, String> metadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("presetKey", "stored:baseline");
        metadata.put("presetLabel", "Baseline");
        metadata.put("emptyKey", "   ");
        metadata.put("", "ignored");
        metadata.put("nullValue", null);
        return metadata;
    }
}
