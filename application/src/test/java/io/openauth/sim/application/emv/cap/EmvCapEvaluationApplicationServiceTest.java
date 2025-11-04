package io.openauth.sim.application.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.telemetry.EmvCapTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class EmvCapEvaluationApplicationServiceTest {

    private EmvCapEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new EmvCapEvaluationApplicationService();
    }

    @ParameterizedTest(name = "{0} evaluation matches EMV/CAP fixture outputs")
    @MethodSource("emvCapVectorIds")
    void evaluationMatchesFixtureOutputs(String vectorId) {
        EmvCapVector vector = EmvCapVectorFixtures.load(vectorId);
        EmvCapEvaluationApplicationService.EvaluationResult result = service.evaluate(requestFrom(vector), true);

        assertEquals(vector.outputs().otpDecimal(), result.otp(), "otp should match fixture output");
        assertEquals(expectedMaskLength(vector), result.maskLength(), "mask length should match fixture overlay");
        assertEquals(1, result.previews().size(), "preview list should contain the evaluated entry only");
        assertEquals(0, result.previews().get(0).delta(), "central preview entry should report delta 0");
        assertEquals(vector.outputs().otpDecimal(), result.previews().get(0).otp(), "central preview OTP should match");

        EmvCapEvaluationApplicationService.Trace trace = result.traceOptional().orElseThrow();
        assertEquals(expectedMasterKeyDigest(vector), trace.masterKeySha256());
        assertEquals(vector.outputs().sessionKeyHex(), trace.sessionKey());
        assertEquals(
                vector.outputs().generateAcInputTerminalHex(),
                trace.generateAcInput().terminalHex());
        assertEquals(
                vector.outputs().generateAcInputIccHex(),
                trace.generateAcInput().iccHex());
        assertEquals(vector.outputs().generateAcResultHex(), trace.generateAcResult());
        assertEquals(vector.outputs().bitmaskOverlay(), trace.bitmask());
        assertEquals(vector.outputs().maskedDigitsOverlay(), trace.maskedDigits());
        assertEquals(vector.input().issuerApplicationDataHex(), trace.issuerApplicationData());

        TelemetryFrame frame = result.telemetry().emit(adapterFor(vector.input().mode()), "telemetry-" + vectorId);
        assertEquals("success", frame.status(), "telemetry status");
        assertEquals(vector.input().mode().name(), frame.fields().get("mode"));
        assertEquals(vector.input().atcHex(), frame.fields().get("atc"));
        assertEquals(expectedMaskLength(vector), frame.fields().get("maskedDigitsCount"));
        assertTrue(frame.sanitized(), "telemetry frame should redact sensitive data");
    }

    @Test
    void identifyModeProducesExpectedOtpAndTelemetry() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        EmvCapEvaluationApplicationService.EvaluationRequest request = requestFrom(vector);

        EmvCapEvaluationApplicationService.EvaluationResult result = service.evaluate(request, true);

        assertEquals("42511495", result.otp(), "otp should match fixture output");
        assertEquals(8, result.maskLength(), "mask length should match masked digit count");
        assertTrue(result.traceOptional().isPresent(), "trace should be present when verbose requested");

        EmvCapEvaluationApplicationService.Trace trace = result.traceOptional().orElseThrow();
        assertEquals(expectedMasterKeyDigest(vector), trace.masterKeySha256());
        assertEquals("5EC8B98ABC8F9E7597647CBCB9A75402", trace.sessionKey());
        assertEquals(
                "0000000000000000000000000000800000000000000000000000000000",
                trace.generateAcInput().terminalHex());
        assertEquals("100000B4A50006040000", trace.generateAcInput().iccHex());
        assertEquals("8000B47F32A79FDA94564306770A03A48000", trace.generateAcResult());
        assertEquals("....1F...........FFFFF..........8...", trace.bitmask());
        assertEquals("....14...........45643..........8...", trace.maskedDigits());
        assertEquals("06770A03A48000", trace.issuerApplicationData());

        TelemetryFrame frame = result.telemetry().emit(TelemetryContracts.emvCapIdentifyAdapter(), "telemetry-001");
        assertEquals("emv.cap.identify", frame.event(), "telemetry event");
        assertEquals("success", frame.status(), "telemetry status");
        assertTrue(frame.sanitized(), "telemetry frame should be sanitized");
        assertEquals("IDENTIFY", frame.fields().get("mode"));
        assertEquals("00B4", frame.fields().get("atc"));
        assertEquals(18, frame.fields().get("ipbMaskLength"));
        assertEquals(8, frame.fields().get("maskedDigitsCount"));
        assertFalse(frame.fields().containsKey("masterKey"));
        assertFalse(frame.fields().containsKey("sessionKey"));
    }

    @Test
    void identifyModeRejectsChallengeAndCapturesValidationTelemetry() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        EmvCapEvaluationApplicationService.CustomerInputs inputs =
                new EmvCapEvaluationApplicationService.CustomerInputs("123456", "", "");
        EmvCapEvaluationApplicationService.EvaluationRequest request =
                new EmvCapEvaluationApplicationService.EvaluationRequest(
                        EmvCapMode.IDENTIFY,
                        vector.input().masterKeyHex(),
                        vector.input().atcHex(),
                        vector.input().branchFactor(),
                        vector.input().height(),
                        0,
                        0,
                        vector.input().ivHex(),
                        vector.input().cdol1Hex(),
                        vector.input().issuerProprietaryBitmapHex(),
                        inputs,
                        EmvCapEvaluationApplicationService.TransactionData.empty(),
                        vector.input().iccDataTemplateHex(),
                        vector.input().issuerApplicationDataHex());

        EmvCapEvaluationApplicationService.EvaluationResult result = service.evaluate(request, false);

        assertEquals("", result.otp(), "invalid evaluation should not surface an OTP");
        assertEquals(0, result.maskLength(), "invalid evaluation should not surface mask digits");
        assertTrue(result.traceOptional().isEmpty(), "trace should be absent when validation fails");

        TelemetryFrame frame = result.telemetry().emit(TelemetryContracts.emvCapIdentifyAdapter(), "telemetry-002");
        assertEquals("emv.cap.identify", frame.event());
        assertEquals("invalid", frame.status());
        assertEquals("invalid_input", frame.fields().get("reasonCode"));
        assertEquals("IDENTIFY", frame.fields().get("mode"));
        assertEquals("00B4", frame.fields().get("atc"));
        assertTrue(frame.sanitized(), "validation telemetry must be sanitized");
        Map<String, Object> fields = frame.fields();
        assertEquals("Identify mode does not accept a challenge input", fields.get("reason"));
    }

    @Test
    void respondModeAppliesAtcSubstitutionWhenOverridesAbsent() {
        EmvCapVector vector = EmvCapVectorFixtures.load("respond-baseline");
        EmvCapEvaluationApplicationService.EvaluationRequest request = requestFrom(vector);

        EmvCapEvaluationApplicationService.EvaluationResult result = service.evaluate(request, true);

        EmvCapEvaluationApplicationService.Trace trace = result.traceOptional().orElseThrow();
        assertTrue(
                trace.generateAcInput().iccHex().contains(vector.input().atcHex()),
                "ICC payload should include substituted ATC when overrides absent");
    }

    @Test
    void verboseToggleDisablesTraceWhenSetToFalse() {
        EmvCapVector vector = EmvCapVectorFixtures.load("sign-baseline");
        EmvCapEvaluationApplicationService.EvaluationRequest request = requestFrom(vector);

        EmvCapEvaluationApplicationService.EvaluationResult result = service.evaluate(request, false);

        assertTrue(result.traceOptional().isEmpty(), "trace should be omitted when verbose flag is false");
    }

    @Test
    void previewWindowGeneratesNeighbouringEntries() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        EmvCapEvaluationApplicationService.EvaluationRequest request =
                new EmvCapEvaluationApplicationService.EvaluationRequest(
                        vector.input().mode(),
                        vector.input().masterKeyHex(),
                        vector.input().atcHex(),
                        vector.input().branchFactor(),
                        vector.input().height(),
                        1,
                        2,
                        vector.input().ivHex(),
                        vector.input().cdol1Hex(),
                        vector.input().issuerProprietaryBitmapHex(),
                        new EmvCapEvaluationApplicationService.CustomerInputs(
                                vector.input().customerInputs().challenge(),
                                vector.input().customerInputs().reference(),
                                vector.input().customerInputs().amount()),
                        new EmvCapEvaluationApplicationService.TransactionData(
                                vector.input().transactionData().terminalHexOverride(),
                                vector.input().transactionData().iccHexOverride()),
                        vector.input().iccDataTemplateHex(),
                        vector.input().issuerApplicationDataHex());

        EmvCapEvaluationApplicationService.EvaluationResult result = service.evaluate(request, true);

        assertEquals(4, result.previews().size(), "preview list should include backward and forward entries");
        assertEquals(-1, result.previews().get(0).delta());
        assertEquals(0, result.previews().get(1).delta());
        assertEquals(1, result.previews().get(2).delta());
        assertEquals(2, result.previews().get(3).delta());
        assertEquals(
                vector.outputs().otpDecimal(), result.previews().get(1).otp(), "delta 0 OTP should match baseline");
    }

    private static EmvCapEvaluationApplicationService.EvaluationRequest requestFrom(EmvCapVector vector) {
        return new EmvCapEvaluationApplicationService.EvaluationRequest(
                vector.input().mode(),
                vector.input().masterKeyHex(),
                vector.input().atcHex(),
                vector.input().branchFactor(),
                vector.input().height(),
                0,
                0,
                vector.input().ivHex(),
                vector.input().cdol1Hex(),
                vector.input().issuerProprietaryBitmapHex(),
                new EmvCapEvaluationApplicationService.CustomerInputs(
                        vector.input().customerInputs().challenge(),
                        vector.input().customerInputs().reference(),
                        vector.input().customerInputs().amount()),
                new EmvCapEvaluationApplicationService.TransactionData(
                        vector.input().transactionData().terminalHexOverride(),
                        vector.input().transactionData().iccHexOverride()),
                vector.input().iccDataTemplateHex(),
                vector.input().issuerApplicationDataHex());
    }

    private static Stream<String> emvCapVectorIds() {
        return Stream.of(
                "identify-baseline",
                "identify-b2-h6",
                "identify-b6-h10",
                "respond-baseline",
                "respond-challenge4",
                "respond-challenge8",
                "sign-baseline",
                "sign-amount-0845",
                "sign-amount-50375");
    }

    private static int expectedMaskLength(EmvCapVector vector) {
        String overlay = vector.outputs().maskedDigitsOverlay();
        int count = 0;
        for (int index = 0; index < overlay.length(); index++) {
            if (overlay.charAt(index) != '.') {
                count++;
            }
        }
        return count;
    }

    private static String expectedMasterKeyDigest(EmvCapVector vector) {
        return sha256Digest(vector.input().masterKeyHex());
    }

    private static String sha256Digest(String hex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(hexToBytes(hex));
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.trim().toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex input must contain an even number of characters");
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
        }
        return builder.toString();
    }

    private static EmvCapTelemetryAdapter adapterFor(EmvCapMode mode) {
        return switch (mode) {
            case IDENTIFY -> TelemetryContracts.emvCapIdentifyAdapter();
            case RESPOND -> TelemetryContracts.emvCapRespondAdapter();
            case SIGN -> TelemetryContracts.emvCapSignAdapter();
        };
    }
}
