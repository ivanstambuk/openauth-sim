package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
final class EmvCapEvaluationService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.emv.cap.telemetry");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^[0-9A-FX]+$");

    private final EmvCapEvaluationApplicationService applicationService;

    EmvCapEvaluationService(EmvCapEvaluationApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    }

    EmvCapEvaluationResponse evaluate(EmvCapEvaluationRequest request) {
        if (request == null) {
            throw validation("invalid_request", "Request body is required", Map.of());
        }

        boolean includeTrace = request.includeTrace() == null || Boolean.TRUE.equals(request.includeTrace());

        EmvCapMode mode;
        try {
            mode = EmvCapMode.fromLabel(requireText(request.mode(), "mode").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw validation(
                    "invalid_mode",
                    "Mode must be IDENTIFY, RESPOND, or SIGN",
                    Map.of("field", "mode", "allowedValues", "IDENTIFY, RESPOND, SIGN"));
        }

        String masterKey = requireHex(request.masterKey(), "masterKey");
        String atc = requireHex(request.atc(), "atc");
        int branchFactor = requirePositive(request.branchFactor(), "branchFactor");
        int height = requirePositive(request.height(), "height");
        String iv = requireHex(request.iv(), "iv");
        String cdol1 = requireHex(request.cdol1(), "cdol1");
        String issuerProprietaryBitmap = requireHex(request.issuerProprietaryBitmap(), "issuerProprietaryBitmap");
        String iccTemplate = requireTemplate(request.iccDataTemplate(), "iccDataTemplate");
        String issuerApplicationData = requireHex(request.issuerApplicationData(), "issuerApplicationData");

        EmvCapEvaluationRequest.CustomerInputs inputsPayload = request.customerInputs() != null
                ? request.customerInputs()
                : new EmvCapEvaluationRequest.CustomerInputs(null, null, null);
        CustomerInputs customerInputs = new CustomerInputs(
                safeDecimal(inputsPayload.challenge()),
                safeDecimal(inputsPayload.reference()),
                safeDecimal(inputsPayload.amount()));

        EmvCapEvaluationRequest.TransactionData txPayload = request.transactionData();
        TransactionData transactionData = txPayload == null
                ? TransactionData.empty()
                : new TransactionData(
                        optionalHex(txPayload.terminal(), "transactionData.terminal"),
                        optionalHex(txPayload.icc(), "transactionData.icc"));

        EvaluationRequest evaluationRequest;
        try {
            evaluationRequest = new EvaluationRequest(
                    mode,
                    masterKey,
                    atc,
                    branchFactor,
                    height,
                    iv,
                    cdol1,
                    issuerProprietaryBitmap,
                    customerInputs,
                    transactionData,
                    iccTemplate,
                    issuerApplicationData);
        } catch (IllegalArgumentException ex) {
            throw validation(
                    "invalid_input", normalizeMessage(ex.getMessage()), Map.of("field", extractField(ex.getMessage())));
        }

        EvaluationResult result = applicationService.evaluate(evaluationRequest, includeTrace);
        TelemetrySignal signal = result.telemetry();
        TelemetryFrame frame = result.telemetryFrame(nextTelemetryId());

        logTelemetry(frame);

        if (signal.status() == TelemetryStatus.SUCCESS) {
            return successResponse(result, evaluationRequest, frame, signal, includeTrace);
        }

        if (signal.status() == TelemetryStatus.INVALID) {
            Map<String, Object> details = new LinkedHashMap<>(signal.fields());
            if (signal.reason() != null && !signal.reason().isBlank()) {
                details.put("reason", signal.reason().trim().replaceAll("\\s+", " "));
            }
            details.putIfAbsent("mode", mode.name());
            details.putIfAbsent("sanitized", signal.sanitized());
            throw validation(signal.reasonCode(), "EMV/CAP input validation failed", details);
        }

        Map<String, Object> details = new LinkedHashMap<>(signal.fields());
        details.putIfAbsent("mode", mode.name());
        details.putIfAbsent("sanitized", signal.sanitized());
        if (signal.reason() != null && !signal.reason().isBlank()) {
            details.put("reason", signal.reason().trim().replaceAll("\\s+", " "));
        }
        throw unexpected("EMV/CAP evaluation failed unexpectedly", details);
    }

    private EmvCapEvaluationResponse successResponse(
            EvaluationResult result,
            EvaluationRequest evaluationRequest,
            TelemetryFrame frame,
            TelemetrySignal signal,
            boolean includeTrace) {
        EmvCapTracePayload tracePayload = null;
        if (includeTrace) {
            tracePayload = result.traceOptional()
                    .map(trace -> toTracePayload(trace, evaluationRequest))
                    .orElse(null);
        }
        EmvCapTelemetryPayload telemetryPayload = new EmvCapTelemetryPayload(frame, signal.reasonCode());
        return new EmvCapEvaluationResponse(result.otp(), result.maskLength(), tracePayload, telemetryPayload);
    }

    private EmvCapTracePayload toTracePayload(Trace trace, EvaluationRequest evaluationRequest) {
        if (trace == null) {
            return null;
        }
        EmvCapTracePayload.GenerateAcInput generateAcInput = new EmvCapTracePayload.GenerateAcInput(
                trace.generateAcInput().terminalHex(), trace.generateAcInput().iccHex());
        return new EmvCapTracePayload(
                trace.masterKeySha256(),
                trace.sessionKey(),
                generateAcInput,
                trace.generateAcResult(),
                trace.bitmask(),
                trace.maskedDigits(),
                trace.issuerApplicationData(),
                evaluationRequest.iccDataTemplateHex(),
                trace.generateAcInput().iccHex());
    }

    private static String safeDecimal(String value) {
        return value == null ? "" : value.trim();
    }

    private static Optional<String> optionalHex(String value, String field) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw validation(
                    "invalid_hex_length",
                    field + " must contain an even number of hex characters",
                    Map.of("field", field));
        }
        if (!HEX_PATTERN.matcher(normalized).matches()) {
            throw validation("invalid_hex", field + " must be hexadecimal", Map.of("field", field));
        }
        return Optional.of(normalized);
    }

    private static String requireTemplate(String value, String field) {
        String normalized = requireText(value, field).toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw validation(
                    "invalid_template_length",
                    field + " must contain an even number of characters",
                    Map.of("field", field));
        }
        if (!TEMPLATE_PATTERN.matcher(normalized).matches()) {
            throw validation(
                    "invalid_template",
                    field + " must contain hexadecimal characters or 'X' placeholders",
                    Map.of("field", field));
        }
        return normalized;
    }

    private static String requireHex(String value, String field) {
        String normalized = requireText(value, field).toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw validation(
                    "invalid_hex_length",
                    field + " must contain an even number of hex characters",
                    Map.of("field", field));
        }
        if (!HEX_PATTERN.matcher(normalized).matches()) {
            throw validation("invalid_hex", field + " must be hexadecimal", Map.of("field", field));
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null) {
            throw validation("missing_field", field + " is required", Map.of("field", field));
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw validation("missing_field", field + " is required", Map.of("field", field));
        }
        return trimmed;
    }

    private static int requirePositive(Integer value, String field) {
        if (value == null) {
            throw validation("missing_field", field + " is required", Map.of("field", field));
        }
        if (value <= 0) {
            throw validation("invalid_number", field + " must be positive", Map.of("field", field));
        }
        return value;
    }

    private static void logTelemetry(TelemetryFrame frame) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=")
                .append(frame.event())
                .append(" status=")
                .append(frame.status());
        frame.fields().forEach((key, value) -> {
            if (!"sessionKey".equalsIgnoreCase(key) && !"masterKey".equalsIgnoreCase(key)) {
                builder.append(' ').append(key).append('=').append(value);
            }
        });
        TELEMETRY_LOGGER.fine(builder.toString());
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "Invalid input";
        }
        return message.trim().replaceAll("\\s+", " ");
    }

    private static String extractField(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String trimmed = message.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex);
        }
        return trimmed;
    }

    private static String nextTelemetryId() {
        return "rest-emv-cap-" + UUID.randomUUID();
    }

    private static EmvCapEvaluationValidationException validation(
            String reasonCode, String message, Map<String, Object> details) {
        return new EmvCapEvaluationValidationException(reasonCode, message, details);
    }

    private static EmvCapEvaluationUnexpectedException unexpected(String message, Map<String, Object> details) {
        return new EmvCapEvaluationUnexpectedException(message, null, details);
    }
}
