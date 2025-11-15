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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.openauth.sim.core.emv.cap.EmvCapReplayMismatchFixtures;
import io.openauth.sim.core.emv.cap.EmvCapReplayMismatchFixtures.MismatchFixture;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
                "00B4",
                4,
                8,
                6,
                0,
                0,
                MASTER_KEY_SHA256,
                "0011",
                new GenerateAcInput("00AA", "00BB"),
                "1000XXXX",
                "100000B4A50006040000",
                "C0FFEE",
                "bitmask",
                "123456",
                "00CC",
                sampleProvenance());

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
                "00B4",
                5,
                7,
                6,
                0,
                0,
                MASTER_KEY_SHA256,
                "00FF",
                new GenerateAcInput("00AA", "00BB"),
                "1000XXXX",
                "100000B4A50006040000",
                "DEADBEEF",
                "mask",
                "654321",
                "00CC",
                sampleProvenance());

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
        EmvCapTracePayload tracePayload = response.trace().trace();
        assertEquals(MASTER_KEY_SHA256, tracePayload.masterKeySha256());
        assertEquals(trace.provenance().decimalizationOverlay().otp(), tracePayload.expectedOtp());
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
                "002A",
                5,
                9,
                6,
                1,
                2,
                MASTER_KEY_SHA256,
                "00AB",
                new GenerateAcInput("00CC", "00DD"),
                "1000XXXX",
                "100000B4A50006040000",
                "C001D00D",
                "mask",
                "123456",
                "0011AABBCCDDEE22",
                sampleProvenance());

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
        EmvCapTracePayload inlineTrace = response.trace().trace();
        assertEquals("C001D00D", inlineTrace.generateAcResult());
        assertNull(inlineTrace.expectedOtp(), "Matched replay traces should not expose expectedOtp");
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

    @Test
    @DisplayName("Metadata exposes expectedOtpHash when telemetry includes it")
    void metadataIncludesExpectedOtpHash() {
        MismatchFixture mismatch = EmvCapReplayMismatchFixtures.load("inline-sign-mismatch");
        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("telemetryId", "rest-emv-cap-inline");
        telemetryFields.put("suppliedOtpLength", mismatch.mismatchOtpDecimal().length());
        telemetryFields.put("branchFactor", 4);
        telemetryFields.put("height", 8);
        telemetryFields.put("ipbMaskLength", 16);
        telemetryFields.put("expectedOtpHash", mismatch.expectedOtpHash());

        TelemetrySignal signal = new TelemetrySignal(
                mismatch.mode(), TelemetryStatus.INVALID, "otp_mismatch", "OTP mismatch", true, telemetryFields);

        ReplayResult result = new ReplayResult(
                signal,
                false,
                OptionalInt.empty(),
                "inline",
                Optional.empty(),
                1,
                1,
                mismatch.mode(),
                Optional.empty(),
                Optional.empty());

        when(applicationService.replay(any(ReplayCommand.class), any(Boolean.class)))
                .thenReturn(result);

        EmvCapReplayRequest request = new EmvCapReplayRequest(
                null,
                mismatch.mode().name(),
                mismatch.mismatchOtpDecimal(),
                1,
                1,
                Boolean.TRUE,
                "0123456789ABCDEF0123456789ABCDEF",
                "0001",
                4,
                8,
                "00000000000000000000000000000000",
                "9F0206",
                "00001F00000000000FFFFF00000000008000",
                new EmvCapReplayRequest.CustomerInputs("1234", "5678", "50375"),
                new EmvCapReplayRequest.TransactionData(null, null),
                "1000xxxxA50006040000",
                "06770A03A48000");

        EmvCapReplayResponse response = service.replay(request);

        Map<String, Object> metadata =
                new ObjectMapper().convertValue(response.metadata(), new TypeReference<Map<String, Object>>() {});
        assertEquals(
                mismatch.expectedOtpHash(),
                metadata.get("expectedOtpHash"),
                "expectedOtpHash should be present in REST metadata when telemetry provides it");
    }

    private static Trace.Provenance sampleProvenance() {
        Trace.Provenance.ProtocolContext protocolContext = new Trace.Provenance.ProtocolContext(
                "CAP-Identify", "IDENTIFY", "4.3", "ARQC", "0x80", "retail-branch", "Sample policy");

        Trace.Provenance.KeyDerivation keyDerivation = new Trace.Provenance.KeyDerivation(
                "IMK-AC",
                "EMV-3DES-ATC-split",
                16,
                MASTER_KEY_SHA256,
                "492181********1234",
                "sha256:5DE415C6A7B13E82D7FE6F410D4F9F1E4636A90BD0E03C03ADF4B7A12D5F7F58",
                "**01",
                "sha256:97E9BE4AC7040CFF67871D395AAC6F6F3BE70A469AFB9B87F3130E9F042F02D1",
                "00B4",
                "0000000000000000",
                "5EC8B98ABC8F9E7597647CBCB9A75402",
                16);

        Trace.Provenance.CdolBreakdown.Entry entry = new Trace.Provenance.CdolBreakdown.Entry(
                0,
                "9F02",
                6,
                "terminal",
                "[00..05]",
                "000000000000",
                new Trace.Provenance.CdolBreakdown.Entry.Decoded("Amount Authorised", "0.00"));
        Trace.Provenance.CdolBreakdown cdolBreakdown = new Trace.Provenance.CdolBreakdown(1, List.of(entry), "000000");

        Trace.Provenance.IadDecoding.Field field = new Trace.Provenance.IadDecoding.Field("cvr", "06770A03");
        Trace.Provenance.IadDecoding iadDecoding = new Trace.Provenance.IadDecoding("06770A03A48000", List.of(field));

        Trace.Provenance.MacTranscript.Block block = new Trace.Provenance.MacTranscript.Block(0, "B0", "CIPHER");
        Trace.Provenance.MacTranscript macTranscript = new Trace.Provenance.MacTranscript(
                "3DES-CBC-MAC",
                "ISO9797-1 Method 2",
                "0000000000000000",
                1,
                List.of(block),
                "8000",
                new Trace.Provenance.MacTranscript.CidFlags(true, false, false, false));

        Trace.Provenance.DecimalizationOverlay.OverlayStep step =
                new Trace.Provenance.DecimalizationOverlay.OverlayStep(4, "1", "1");
        Trace.Provenance.DecimalizationOverlay decimalizationOverlay = new Trace.Provenance.DecimalizationOverlay(
                "ISO-0",
                "00B47F32A79FDA9400B4",
                "00541703287953009400B4",
                "....1F...........FFFFF..........8...",
                List.of(step),
                "140456438",
                9);

        return new Trace.Provenance(
                protocolContext, keyDerivation, cdolBreakdown, iadDecoding, macTranscript, decimalizationOverlay);
    }
}
