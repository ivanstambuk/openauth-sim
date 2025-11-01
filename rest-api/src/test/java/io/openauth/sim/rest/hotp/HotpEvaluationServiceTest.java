package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.core.encoding.Base32SecretCodec;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.rest.EvaluationWindowRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class HotpEvaluationServiceTest {

    private static final String OTP = "755224";

    private HotpEvaluationApplicationService applicationService;
    private HotpEvaluationService service;

    @BeforeEach
    void setUp() {
        applicationService = Mockito.mock(HotpEvaluationApplicationService.class);
        service = new HotpEvaluationService(applicationService);
    }

    @Test
    @DisplayName("Stored evaluation delegates to application service and returns generated OTP")
    void storedEvaluationReturnsGeneratedOtp() {
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.SUCCESS, "generated", null, true, Map.of("credentialSource", "stored"), null);
        List<OtpPreview> previews = List.of(
                new OtpPreview("000099", -1, "123456"),
                new OtpPreview("000100", 0, OTP),
                new OtpPreview("000101", 1, "789012"));
        EvaluationResult result = new EvaluationResult(
                signal, true, "demo", 0L, 1L, HotpHashAlgorithm.SHA1, 6, OTP, null, null, previews, null);
        when(applicationService.evaluate(any(EvaluationCommand.Stored.class), anyBoolean()))
                .thenReturn(result);

        HotpEvaluationResponse response = service.evaluateStored(
                new HotpStoredEvaluationRequest("demo", new EvaluationWindowRequest(0, 0), null));

        assertEquals("generated", response.status());
        assertEquals(OTP, response.otp());
        assertEquals("stored", response.metadata().credentialSource());
        assertEquals("demo", response.metadata().credentialId());
        assertEquals(3, response.previews().size());
        assertEquals(-1, response.previews().get(0).delta());
        assertEquals("123456", response.previews().get(0).otp());
        assertEquals("000099", response.previews().get(0).counter());
        verify(applicationService).evaluate(any(EvaluationCommand.Stored.class), anyBoolean());
    }

    @Test
    @DisplayName("Inline evaluation delegates to application service and returns generated OTP")
    void inlineEvaluationReturnsGeneratedOtp() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", "inline");
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "generated", null, true, fields, null);
        List<OtpPreview> previews = List.of(new OtpPreview("000010", 0, OTP));
        EvaluationResult result = new EvaluationResult(
                signal, false, null, 10L, 11L, HotpHashAlgorithm.SHA1, 6, OTP, null, null, previews, null);
        when(applicationService.evaluate(any(EvaluationCommand.Inline.class), anyBoolean()))
                .thenReturn(result);

        HotpEvaluationResponse response = service.evaluateInline(new HotpInlineEvaluationRequest(
                "3132333435363738393031323334353637383930",
                null,
                "SHA1",
                6,
                new EvaluationWindowRequest(0, 0),
                10L,
                Map.of(),
                null));

        assertEquals("generated", response.status());
        assertEquals(OTP, response.otp());
        assertEquals("inline", response.metadata().credentialSource());
        verify(applicationService).evaluate(any(EvaluationCommand.Inline.class), anyBoolean());
    }

    @Test
    @DisplayName("Inline evaluation accepts Base32 shared secrets")
    void inlineEvaluationAcceptsBase32Secrets() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", "inline");
        TelemetrySignal signal = new TelemetrySignal(TelemetryStatus.SUCCESS, "generated", null, true, fields, null);
        EvaluationResult result = new EvaluationResult(
                signal,
                false,
                null,
                10L,
                11L,
                HotpHashAlgorithm.SHA1,
                6,
                OTP,
                null,
                null,
                List.of(new OtpPreview("000010", 0, OTP)),
                null);
        when(applicationService.evaluate(any(EvaluationCommand.Inline.class), anyBoolean()))
                .thenReturn(result);

        String base32 = "MFRGGZDFMZTWQ2LK"; // RFC 3548 example
        service.evaluateInline(new HotpInlineEvaluationRequest(
                null, base32, "SHA1", 6, new EvaluationWindowRequest(0, 0), 10L, Map.of(), null));

        ArgumentCaptor<EvaluationCommand.Inline> captor = ArgumentCaptor.forClass(EvaluationCommand.Inline.class);
        verify(applicationService).evaluate(captor.capture(), anyBoolean());
        assertEquals(Base32SecretCodec.toUpperHex(base32), captor.getValue().sharedSecretHex());
    }

    @Test
    @DisplayName("Inline evaluation rejects unknown algorithms with sanitized details")
    void inlineEvaluationRejectsUnknownAlgorithm() {
        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateInline(new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        null,
                        "sha999",
                        6,
                        new EvaluationWindowRequest(0, 0),
                        0L,
                        Map.of(),
                        null)));

        assertEquals("algorithm_invalid", exception.reasonCode());
        assertEquals("algorithm", exception.details().get("field"));
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Inline evaluation requires counter metadata")
    void inlineEvaluationRequiresCounter() {
        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateInline(new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        null,
                        "SHA1",
                        6,
                        new EvaluationWindowRequest(0, 0),
                        null,
                        Map.of(),
                        null)));

        assertEquals("counter_required", exception.reasonCode());
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Inline evaluation requires shared secret material")
    void inlineEvaluationRequiresSecret() {
        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateInline(new HotpInlineEvaluationRequest(
                        "   ", null, "SHA1", 6, new EvaluationWindowRequest(0, 0), 0L, Map.of(), null)));

        assertEquals("shared_secret_required", exception.reasonCode());
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Inline evaluation rejects conflicting secret encodings")
    void inlineEvaluationRejectsSecretConflicts() {
        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateInline(new HotpInlineEvaluationRequest(
                        "31323334",
                        "MFRGGZDFMZTWQ2LK",
                        "SHA1",
                        6,
                        new EvaluationWindowRequest(0, 0),
                        0L,
                        Map.of(),
                        null)));

        assertEquals("shared_secret_conflict", exception.reasonCode());
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Inline evaluation rejects invalid Base32 secrets")
    void inlineEvaluationRejectsInvalidBase32() {
        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateInline(new HotpInlineEvaluationRequest(
                        null, "!!!!", "SHA1", 6, new EvaluationWindowRequest(0, 0), 0L, Map.of(), null)));

        assertEquals("shared_secret_base32_invalid", exception.reasonCode());
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Stored evaluation requires credential identifiers")
    void storedEvaluationRequiresCredentialId() {
        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateStored(
                        new HotpStoredEvaluationRequest("  ", new EvaluationWindowRequest(0, 0), null)));

        assertEquals("credentialId_required", exception.reasonCode());
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Stored evaluation surfaces counter overflow as validation error")
    void storedEvaluationCounterOverflow() {
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.INVALID,
                "counter_overflow",
                "overflow",
                true,
                Map.of("credentialSource", "stored"),
                null);
        EvaluationResult result = new EvaluationResult(
                signal,
                true,
                "demo",
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                HotpHashAlgorithm.SHA1,
                6,
                null,
                null,
                null,
                List.of(),
                null);
        when(applicationService.evaluate(any(EvaluationCommand.Stored.class), anyBoolean()))
                .thenReturn(result);

        HotpEvaluationValidationException exception = assertThrows(
                HotpEvaluationValidationException.class,
                () -> service.evaluateStored(
                        new HotpStoredEvaluationRequest("demo", new EvaluationWindowRequest(0, 0), null)));

        assertEquals("counter_overflow", exception.reasonCode());
        verify(applicationService).evaluate(any(EvaluationCommand.Stored.class), anyBoolean());
    }

    @Test
    @DisplayName("Inline evaluation surfaces unexpected errors")
    void inlineEvaluationUnexpectedError() {
        TelemetrySignal signal = new TelemetrySignal(
                TelemetryStatus.ERROR, "unexpected_error", "boom", false, Map.of("credentialSource", "inline"), null);
        EvaluationResult result = new EvaluationResult(
                signal, false, null, 5L, 5L, HotpHashAlgorithm.SHA1, 6, null, null, null, List.of(), null);
        when(applicationService.evaluate(any(EvaluationCommand.Inline.class), anyBoolean()))
                .thenReturn(result);

        HotpEvaluationUnexpectedException exception = assertThrows(
                HotpEvaluationUnexpectedException.class,
                () -> service.evaluateInline(new HotpInlineEvaluationRequest(
                        "3132333435363738393031323334353637383930",
                        null,
                        "SHA1",
                        6,
                        new EvaluationWindowRequest(0, 0),
                        5L,
                        Map.of(),
                        null)));

        assertEquals("inline", exception.credentialSource());
        assertEquals(Boolean.FALSE, exception.details().get("sanitized"));
        assertTrue(exception.getMessage().contains("boom"));
        verify(applicationService).evaluate(any(EvaluationCommand.Inline.class), anyBoolean());
    }
}
