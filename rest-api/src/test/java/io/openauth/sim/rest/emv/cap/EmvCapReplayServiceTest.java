package io.openauth.sim.rest.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace.GenerateAcInput;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayResult;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class EmvCapReplayServiceTest {

    private static final String MASTER_KEY_SHA256 =
            "sha256:0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

    private EmvCapReplayApplicationService applicationService;
    private EmvCapReplayService service;

    @BeforeEach
    void setUp() {
        applicationService = Mockito.mock(EmvCapReplayApplicationService.class);
        service = new EmvCapReplayService(applicationService);
    }

    @Test
    @DisplayName("Stored replay omits verbose trace when includeTrace=false")
    void storedReplayOmitsTraceWhenIncludeTraceDisabled() {
        EvaluationRequest evaluationRequest = new EvaluationRequest(
                EmvCapMode.IDENTIFY,
                "00112233445566778899AABBCCDDEEFF",
                "0001",
                4,
                8,
                0,
                0,
                "00000000000000000000000000000000",
                "9F0206",
                "00001F00000000000FFFFF00000000008000",
                new CustomerInputs("123", "456", "789"),
                TransactionData.empty(),
                "1000",
                "06770A03A48000");

        Trace trace = new Trace(
                MASTER_KEY_SHA256, "0011", new GenerateAcInput("00AA", "00BB"), "C0FFEE", "bitmask", "123456", "00CC");

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("telemetryId", "rest-emv-cap-test");
        telemetryFields.put("branchFactor", 4);
        telemetryFields.put("height", 8);
        telemetryFields.put("ipbMaskLength", 16);
        telemetryFields.put("suppliedOtpLength", 6);

        TelemetrySignal signal =
                new TelemetrySignal(EmvCapMode.IDENTIFY, TelemetryStatus.SUCCESS, "match", null, true, telemetryFields);

        ReplayResult result = new ReplayResult(
                signal,
                true,
                OptionalInt.of(1),
                "stored",
                Optional.of("stored-credential"),
                2,
                3,
                EmvCapMode.IDENTIFY,
                Optional.of(evaluationRequest),
                Optional.of(trace));

        when(applicationService.replay(any(ReplayCommand.class), any(Boolean.class)))
                .thenReturn(result);

        EmvCapReplayRequest request = new EmvCapReplayRequest(
                "stored-credential",
                "IDENTIFY",
                "123456",
                2,
                3,
                Boolean.FALSE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        EmvCapReplayResponse response = service.replay(request);

        assertEquals("match", response.status());
        assertEquals("match", response.reasonCode());
        assertNull(response.trace(), "Trace payload should be omitted when includeTrace is false");
        assertEquals("stored", response.metadata().credentialSource());
        assertEquals("stored-credential", response.metadata().credentialId());
        assertEquals(Integer.valueOf(1), response.metadata().matchedDelta());
        assertEquals(Integer.valueOf(4), response.metadata().branchFactor());
        assertEquals(Integer.valueOf(8), response.metadata().height());
        assertEquals(Integer.valueOf(18), response.metadata().ipbMaskLength());
        assertEquals(Integer.valueOf(6), response.metadata().suppliedOtpLength());
        assertEquals("rest-emv-cap-test", response.metadata().telemetryId());

        ArgumentCaptor<ReplayCommand> commandCaptor = ArgumentCaptor.forClass(ReplayCommand.class);
        verify(applicationService).replay(commandCaptor.capture(), eq(false));
        assertTrue(commandCaptor.getValue() instanceof ReplayCommand.Stored);
    }

    @Test
    @DisplayName("Stored replay returns verbose trace for OTP mismatch when includeTrace=true")
    void storedReplayReturnsTraceWhenIncludeTraceEnabled() {
        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("telemetryId", "rest-emv-cap-trace");
        telemetryFields.put("branchFactor", 5);
        telemetryFields.put("height", 7);
        telemetryFields.put("ipbMaskLength", 12);
        telemetryFields.put("suppliedOtpLength", 6);

        Trace trace = new Trace(
                MASTER_KEY_SHA256, "00FF", new GenerateAcInput("00AA", "00BB"), "DEADBEEF", "mask", "654321", "00CC");

        TelemetrySignal signal = new TelemetrySignal(
                EmvCapMode.SIGN, TelemetryStatus.INVALID, "otp_mismatch", "OTP mismatch", true, telemetryFields);

        ReplayResult result = new ReplayResult(
                signal,
                false,
                OptionalInt.empty(),
                "stored",
                Optional.of("stored-credential"),
                0,
                0,
                EmvCapMode.SIGN,
                Optional.empty(),
                Optional.of(trace));

        when(applicationService.replay(any(ReplayCommand.class), any(Boolean.class)))
                .thenReturn(result);

        EmvCapReplayRequest request = new EmvCapReplayRequest(
                "stored-credential",
                "SIGN",
                "987654",
                0,
                0,
                Boolean.TRUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        EmvCapReplayResponse response = service.replay(request);

        assertEquals("mismatch", response.status());
        assertEquals("otp_mismatch", response.reasonCode());
        assertNotNull(response.trace(), "Trace payload should be included for otp_mismatch when includeTrace is true");
        assertEquals(MASTER_KEY_SHA256, response.trace().masterKeySha256());
        assertEquals("emv.cap.replay.stored", response.trace().payload().operation());
        assertEquals("stored", response.metadata().credentialSource());
        assertEquals("stored-credential", response.metadata().credentialId());
        assertEquals(Integer.valueOf(12), response.metadata().ipbMaskLength());
        assertEquals(Integer.valueOf(6), response.metadata().suppliedOtpLength());

        assertEquals("rest-emv-cap-trace", response.metadata().telemetryId());

        verify(applicationService).replay(any(ReplayCommand.class), eq(true));
    }

    @Test
    @DisplayName("Inline replay command forwards overrides and returns verbose trace")
    void inlineReplayBuildsCommandAndReturnsTrace() {
        EmvCapReplayRequest request = new EmvCapReplayRequest(
                null,
                "RESPOND",
                "654321",
                1,
                2,
                null,
                "0123456789ABCDEF0123456789ABCDEF",
                "002A",
                5,
                9,
                "00112233445566778899AABBCCDDEEFF",
                "A1B2C3D4",
                "AABBCCDDEEFF0011",
                new EmvCapReplayRequest.CustomerInputs(" 321 ", " 123 ", "  42.00"),
                new EmvCapReplayRequest.TransactionData(" 1122 ", " 3344"),
                "9F1007AABBCCDD",
                "0011AABBCCDDEE22");

        EvaluationRequest evaluationRequest = new EvaluationRequest(
                EmvCapMode.RESPOND,
                "0123456789ABCDEF0123456789ABCDEF",
                "002A",
                5,
                9,
                1,
                2,
                "00112233445566778899AABBCCDDEEFF",
                "A1B2C3D4",
                "AABBCCDDEEFF0011",
                new CustomerInputs("321", "123", "42.00"),
                new TransactionData(Optional.of("1122"), Optional.of("3344")),
                "9F1007AABBCCDD",
                "0011AABBCCDDEE22");

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("telemetryId", "rest-inline-replay");
        telemetryFields.put("branchFactor", 5);
        telemetryFields.put("height", 9);
        telemetryFields.put("ipbMaskLength", 8);
        telemetryFields.put("suppliedOtpLength", 6);

        Trace trace = new Trace(
                MASTER_KEY_SHA256,
                "00AB",
                new GenerateAcInput("00CC", "00DD"),
                "C001D00D",
                "mask",
                "123456",
                "0011AABBCCDDEE22");

        TelemetrySignal signal =
                new TelemetrySignal(EmvCapMode.RESPOND, TelemetryStatus.SUCCESS, "match", null, true, telemetryFields);

        ReplayResult result = new ReplayResult(
                signal,
                true,
                OptionalInt.of(0),
                "inline",
                Optional.empty(),
                1,
                2,
                EmvCapMode.RESPOND,
                Optional.of(evaluationRequest),
                Optional.of(trace));

        when(applicationService.replay(any(ReplayCommand.class), any(Boolean.class)))
                .thenReturn(result);

        EmvCapReplayResponse response = service.replay(request);

        assertEquals("match", response.status());
        assertEquals("match", response.reasonCode());
        assertNotNull(response.trace(), "Trace payload should be returned when includeTrace defaults to true");
        assertEquals("inline", response.metadata().credentialSource());
        assertNull(response.metadata().credentialId());
        assertEquals(Integer.valueOf(0), response.metadata().matchedDelta());
        assertEquals(Integer.valueOf(5), response.metadata().branchFactor());
        assertEquals(Integer.valueOf(9), response.metadata().height());
        assertEquals(Integer.valueOf(8), response.metadata().ipbMaskLength());
        assertEquals(Integer.valueOf(6), response.metadata().suppliedOtpLength());
        assertEquals("rest-inline-replay", response.metadata().telemetryId());

        ArgumentCaptor<ReplayCommand> commandCaptor = ArgumentCaptor.forClass(ReplayCommand.class);
        verify(applicationService).replay(commandCaptor.capture(), eq(true));
        assertTrue(commandCaptor.getValue() instanceof ReplayCommand.Inline);
        ReplayCommand.Inline inlineCommand = (ReplayCommand.Inline) commandCaptor.getValue();
        assertEquals("654321", inlineCommand.otp());
        assertEquals(1, inlineCommand.driftBackward());
        assertEquals(2, inlineCommand.driftForward());
        assertEquals(evaluationRequest, inlineCommand.request());
    }

    @Test
    @DisplayName("Invalid telemetry status throws validation exception without sensitive details")
    void invalidTelemetryThrowsValidationException() {
        Map<String, Object> telemetryFields = new HashMap<>();
        telemetryFields.put("telemetryId", "inline-invalid");
        telemetryFields.put("branchFactor", 3);
        telemetryFields.put("height", 4);
        telemetryFields.put("ipbMaskLength", 5);
        telemetryFields.put("suppliedOtpLength", 6);
        telemetryFields.put("masterKey", "should-not-leak");
        telemetryFields.put("sessionKey", "should-not-leak");

        TelemetrySignal signal = new TelemetrySignal(
                EmvCapMode.SIGN,
                TelemetryStatus.INVALID,
                "invalid_input",
                "Invalid override provided",
                false,
                telemetryFields);

        ReplayResult result = new ReplayResult(
                signal,
                false,
                OptionalInt.empty(),
                "inline",
                Optional.empty(),
                0,
                0,
                EmvCapMode.SIGN,
                Optional.empty(),
                Optional.empty());

        when(applicationService.replay(any(ReplayCommand.class), any(Boolean.class)))
                .thenReturn(result);

        EmvCapReplayRequest request = new EmvCapReplayRequest(
                null,
                "SIGN",
                "000000",
                0,
                0,
                Boolean.TRUE,
                "0123456789ABCDEF0123456789ABCDEF",
                "0001",
                2,
                3,
                "0011",
                "AA55",
                "BB66",
                null,
                null,
                "FF00",
                "1122");

        EmvCapReplayValidationException exception =
                assertThrows(EmvCapReplayValidationException.class, () -> service.replay(request));

        assertEquals("invalid_input", exception.reasonCode());
        assertEquals("Invalid override provided", exception.getMessage());
        assertEquals("inline-invalid", exception.details().get("telemetryId"));
        assertFalse(exception.details().containsKey("masterKey"));
        assertFalse(exception.details().containsKey("sessionKey"));
    }

    @Test
    @DisplayName("Error telemetry escalates to unexpected exception")
    void errorTelemetryThrowsUnexpectedException() {
        Map<String, Object> telemetryFields = new HashMap<>();
        telemetryFields.put("telemetryId", "inline-error");
        telemetryFields.put("branchFactor", 2);
        telemetryFields.put("height", 3);
        telemetryFields.put("ipbMaskLength", 4);
        telemetryFields.put("suppliedOtpLength", 6);
        TelemetrySignal signal = new TelemetrySignal(
                EmvCapMode.IDENTIFY,
                TelemetryStatus.ERROR,
                "unexpected_error",
                "Engine failure",
                false,
                telemetryFields);

        ReplayResult result = new ReplayResult(
                signal,
                false,
                OptionalInt.empty(),
                "inline",
                Optional.empty(),
                0,
                0,
                EmvCapMode.IDENTIFY,
                Optional.empty(),
                Optional.empty());

        when(applicationService.replay(any(ReplayCommand.class), any(Boolean.class)))
                .thenReturn(result);

        EmvCapReplayRequest request = new EmvCapReplayRequest(
                "credential",
                "IDENTIFY",
                "123456",
                0,
                0,
                Boolean.TRUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        EmvCapReplayUnexpectedException exception =
                assertThrows(EmvCapReplayUnexpectedException.class, () -> service.replay(request));

        assertEquals("EMV/CAP replay failed unexpectedly", exception.getMessage());
        assertEquals("inline-error", exception.details().get("telemetryId"));
    }

    @Test
    @DisplayName("Negative drift window inputs fail validation")
    void negativeDriftWindowFailsValidation() {
        EmvCapReplayRequest request = new EmvCapReplayRequest(
                "credential",
                "IDENTIFY",
                "123456",
                -1,
                0,
                Boolean.FALSE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        EmvCapReplayValidationException exception =
                assertThrows(EmvCapReplayValidationException.class, () -> service.replay(request));

        assertEquals("invalid_window", exception.reasonCode());
        assertEquals("driftBackward must be non-negative", exception.getMessage());
        assertEquals("driftBackward", exception.details().get("field"));
    }

    @Test
    @DisplayName("Null request payload fails validation")
    void nullRequestFailsValidation() {
        EmvCapReplayValidationException exception =
                assertThrows(EmvCapReplayValidationException.class, () -> service.replay(null));

        assertEquals("invalid_request", exception.reasonCode());
        assertEquals("Request body is required", exception.getMessage());
        assertTrue(exception.details().isEmpty());
    }
}
