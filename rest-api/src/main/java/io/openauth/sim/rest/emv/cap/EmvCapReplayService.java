package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayResult;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import io.openauth.sim.rest.VerboseTracePayload;
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
final class EmvCapReplayService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.emv.cap.telemetry");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^[0-9A-FX]+$");
    private static final Pattern OTP_PATTERN = Pattern.compile("^\\d+$");

    private final EmvCapReplayApplicationService applicationService;

    EmvCapReplayService(EmvCapReplayApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    }

    EmvCapReplayResponse replay(EmvCapReplayRequest request) {
        if (request == null) {
            throw validation("invalid_request", "Request body is required", Map.of());
        }

        boolean includeTrace = request.includeTrace() == null || Boolean.TRUE.equals(request.includeTrace());

        EmvCapMode mode = parseMode(request.mode());
        int driftBackward = resolveWindow(request.driftBackward(), "driftBackward");
        int driftForward = resolveWindow(request.driftForward(), "driftForward");
        String otp = requireOtp(request.otp());

        ReplayCommand command = buildCommand(request, mode, otp, driftBackward, driftForward);

        String telemetryId = nextTelemetryId();

        ReplayResult result;
        try {
            result = applicationService.replay(command, includeTrace);
        } catch (IllegalArgumentException ex) {
            throw validation(
                    "invalid_input",
                    normalizeMessage(ex.getMessage()),
                    Map.of("reason", normalizeMessage(ex.getMessage())));
        }

        TelemetrySignal signal = result.telemetry();
        TelemetryFrame frame = result.telemetryFrame(telemetryId);
        logTelemetry(frame);

        String resolvedTelemetryId = resolveTelemetryId(frame, telemetryId);
        Map<String, Object> telemetryFields = frame.fields();
        EmvCapReplayMetadata metadata = buildMetadata(result, resolvedTelemetryId, telemetryFields);

        if (signal.status() == TelemetryStatus.SUCCESS) {
            EmvCapReplayVerboseTracePayload tracePayload = includeTrace
                    ? result.traceOptional()
                            .map(trace -> toTracePayload(result, trace))
                            .orElse(null)
                    : null;
            return new EmvCapReplayResponse("match", signal.reasonCode(), metadata, tracePayload);
        }

        if (signal.status() == TelemetryStatus.INVALID && "otp_mismatch".equals(signal.reasonCode())) {
            EmvCapReplayVerboseTracePayload tracePayload = includeTrace
                    ? result.traceOptional()
                            .map(trace -> toTracePayload(result, trace))
                            .orElse(null)
                    : null;
            return new EmvCapReplayResponse("mismatch", signal.reasonCode(), metadata, tracePayload);
        }

        if (signal.status() == TelemetryStatus.INVALID) {
            Map<String, Object> details = sanitizedDetails(telemetryFields, resolvedTelemetryId);
            throw validation(signal.reasonCode(), normalizeMessage(signal.reason()), details);
        }

        Map<String, Object> details = sanitizedDetails(telemetryFields, resolvedTelemetryId);
        throw unexpected("EMV/CAP replay failed unexpectedly", details);
    }

    private ReplayCommand buildCommand(
            EmvCapReplayRequest request, EmvCapMode mode, String otp, int driftBackward, int driftForward) {
        if (hasText(request.credentialId())) {
            String credentialId = request.credentialId().trim();
            Optional<EvaluationRequest> override =
                    overridesPresent(request) ? Optional.of(buildEvaluationRequest(request, mode)) : Optional.empty();
            return new ReplayCommand.Stored(credentialId, mode, otp, driftBackward, driftForward, override);
        }
        EvaluationRequest evaluationRequest = buildEvaluationRequest(request, mode);
        return new ReplayCommand.Inline(evaluationRequest, otp, driftBackward, driftForward);
    }

    private EvaluationRequest buildEvaluationRequest(EmvCapReplayRequest request, EmvCapMode mode) {
        String masterKey = requireHex(request.masterKey(), "masterKey");
        String atc = requireHex(request.atc(), "atc");
        int branchFactor = requirePositive(request.branchFactor(), "branchFactor");
        int height = requirePositive(request.height(), "height");
        int previewBackward = request.driftBackward() != null ? Math.max(0, request.driftBackward()) : 0;
        int previewForward = request.driftForward() != null ? Math.max(0, request.driftForward()) : 0;
        String iv = requireHex(request.iv(), "iv");
        String cdol1 = requireHex(request.cdol1(), "cdol1");
        String issuerBitmap = requireHex(request.issuerProprietaryBitmap(), "issuerProprietaryBitmap");
        String iccTemplate = requireTemplate(request.iccDataTemplate(), "iccDataTemplate");
        String issuerApplicationData = requireHex(request.issuerApplicationData(), "issuerApplicationData");

        EmvCapReplayRequest.CustomerInputs inputsPayload = request.customerInputs() != null
                ? request.customerInputs()
                : new EmvCapReplayRequest.CustomerInputs(null, null, null);
        CustomerInputs customerInputs = new CustomerInputs(
                safeDecimal(inputsPayload.challenge()),
                safeDecimal(inputsPayload.reference()),
                safeDecimal(inputsPayload.amount()));

        EmvCapReplayRequest.TransactionData transactionPayload = request.transactionData();
        TransactionData transactionData = transactionPayload == null
                ? TransactionData.empty()
                : new TransactionData(
                        optionalHex(transactionPayload.terminal(), "transactionData.terminal"),
                        optionalHex(transactionPayload.icc(), "transactionData.icc"));

        try {
            return new EvaluationRequest(
                    mode,
                    masterKey,
                    atc,
                    branchFactor,
                    height,
                    previewBackward,
                    previewForward,
                    iv,
                    cdol1,
                    issuerBitmap,
                    customerInputs,
                    transactionData,
                    iccTemplate,
                    issuerApplicationData);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", extractField(ex.getMessage()));
            throw validation("invalid_input", normalizeMessage(ex.getMessage()), details);
        }
    }

    private static boolean overridesPresent(EmvCapReplayRequest request) {
        return hasText(request.masterKey())
                || hasText(request.atc())
                || request.branchFactor() != null
                || request.height() != null
                || hasText(request.iv())
                || hasText(request.cdol1())
                || hasText(request.issuerProprietaryBitmap())
                || hasText(request.iccDataTemplate())
                || hasText(request.issuerApplicationData());
    }

    private static EmvCapMode parseMode(String value) {
        String text = requireText(value, "mode").toUpperCase(Locale.ROOT);
        try {
            return EmvCapMode.fromLabel(text);
        } catch (IllegalArgumentException ex) {
            throw validation(
                    "invalid_mode",
                    "Mode must be IDENTIFY, RESPOND, or SIGN",
                    Map.of("field", "mode", "allowedValues", "IDENTIFY, RESPOND, SIGN"));
        }
    }

    private static EmvCapReplayMetadata buildMetadata(
            ReplayResult result, String telemetryId, Map<String, Object> telemetryFields) {
        Integer matchedDelta =
                result.matchedDelta().isPresent() ? result.matchedDelta().getAsInt() : null;
        EvaluationRequest request = result.effectiveRequest().orElse(null);

        Integer branchFactor =
                request != null ? request.branchFactor() : valueAsInteger(telemetryFields, "branchFactor");
        Integer height = request != null ? request.height() : valueAsInteger(telemetryFields, "height");
        Integer ipbMaskLength = request != null
                ? request.issuerProprietaryBitmapHex().length() / 2
                : valueAsInteger(telemetryFields, "ipbMaskLength");
        Integer suppliedOtpLength = valueAsInteger(telemetryFields, "suppliedOtpLength");

        return new EmvCapReplayMetadata(
                result.credentialSource(),
                result.credentialId().orElse(null),
                result.mode().name(),
                matchedDelta,
                result.driftBackward(),
                result.driftForward(),
                branchFactor,
                height,
                ipbMaskLength,
                suppliedOtpLength,
                telemetryId);
    }

    private static EmvCapReplayVerboseTracePayload toTracePayload(ReplayResult result, Trace trace) {
        if (trace == null) {
            return null;
        }
        String operation = "emv.cap.replay." + result.credentialSource();
        Integer suppliedOtpLength = valueAsInteger(result.telemetry().fields(), "suppliedOtpLength");
        VerboseTrace.Builder builder = VerboseTrace.builder(operation)
                .withMetadata("mode", result.mode().name())
                .withMetadata("credentialSource", result.credentialSource())
                .withMetadata("driftBackward", Integer.toString(result.driftBackward()))
                .withMetadata("driftForward", Integer.toString(result.driftForward()))
                .withMetadata("atc", trace.atc())
                .withMetadata("branchFactor", Integer.toString(trace.branchFactor()))
                .withMetadata("height", Integer.toString(trace.height()))
                .withMetadata("maskLength", Integer.toString(trace.maskLength()))
                .withMetadata("previewWindowBackward", Integer.toString(trace.previewWindowBackward()))
                .withMetadata("previewWindowForward", Integer.toString(trace.previewWindowForward()))
                .withMetadata("match", Boolean.toString(result.match()));

        result.matchedDelta().ifPresent(delta -> builder.withMetadata("matchedDelta", Integer.toString(delta)));
        result.credentialId().ifPresent(id -> builder.withMetadata("credentialId", id));

        builder.addStep(step -> step.id("generate_ac")
                .summary("Generated application cryptogram")
                .detail("core.emv.cap.generateAc")
                .attribute(AttributeType.STRING, "masterKey.sha256", trace.masterKeySha256())
                .attribute(AttributeType.HEX, "sessionKey", trace.sessionKey())
                .attribute(
                        AttributeType.HEX,
                        "terminalPayload",
                        trace.generateAcInput().terminalHex())
                .attribute(
                        AttributeType.HEX, "iccPayload", trace.generateAcInput().iccHex())
                .attribute(AttributeType.HEX, "generateAcResult", trace.generateAcResult())
                .attribute(AttributeType.STRING, "bitmask", trace.bitmask())
                .attribute(AttributeType.STRING, "maskedDigits", trace.maskedDigits())
                .attribute(AttributeType.HEX, "issuerApplicationData", trace.issuerApplicationData()));

        builder.addStep(step -> step.id("otp_comparison")
                .summary("Compared supplied OTP against computed values")
                .detail("application.emv.cap.replay.compare")
                .attribute(
                        AttributeType.INT,
                        "suppliedOtpLength",
                        suppliedOtpLength != null
                                ? suppliedOtpLength
                                : trace.maskedDigits().length())
                .attribute(AttributeType.BOOL, "match", result.match())
                .attribute(AttributeType.INT, "driftBackward", result.driftBackward())
                .attribute(AttributeType.INT, "driftForward", result.driftForward()));

        VerboseTracePayload payload = VerboseTracePayload.from(builder.build());
        return new EmvCapReplayVerboseTracePayload(trace.masterKeySha256(), payload);
    }

    private static String nextTelemetryId() {
        return "rest-emv-cap-" + UUID.randomUUID();
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

    private static String resolveTelemetryId(TelemetryFrame frame, String fallback) {
        Object value = frame.fields().get("telemetryId");
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private static Map<String, Object> sanitizedDetails(Map<String, Object> fields, String telemetryId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("telemetryId", telemetryId);
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (!"sessionKey".equalsIgnoreCase(key) && !"masterKey".equalsIgnoreCase(key)) {
                    details.putIfAbsent(key, value);
                }
            });
        }
        return details;
    }

    private static int resolveWindow(Integer value, String field) {
        int resolved = value == null ? 0 : value;
        if (resolved < 0) {
            throw validation("invalid_window", field + " must be non-negative", Map.of("field", field));
        }
        return resolved;
    }

    private static int requirePositive(Integer value, String field) {
        if (value == null) {
            throw validation("invalid_input", field + " is required", Map.of("field", field));
        }
        if (value <= 0) {
            throw validation("invalid_number", field + " must be positive", Map.of("field", field));
        }
        return value;
    }

    private static String requireTemplate(String value, String field) {
        String text = requireText(value, field).toUpperCase(Locale.ROOT);
        if ((text.length() & 1) == 1) {
            throw validation(
                    "invalid_template_length",
                    field + " must contain an even number of characters",
                    Map.of("field", field));
        }
        if (!TEMPLATE_PATTERN.matcher(text).matches()) {
            throw validation(
                    "invalid_template",
                    field + " must contain hexadecimal characters or 'X' placeholders",
                    Map.of("field", field));
        }
        return text;
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

    private static String requireHex(String value, String field) {
        String text = requireText(value, field).toUpperCase(Locale.ROOT);
        if ((text.length() & 1) == 1) {
            throw validation(
                    "invalid_hex_length",
                    field + " must contain an even number of hex characters",
                    Map.of("field", field));
        }
        if (!HEX_PATTERN.matcher(text).matches()) {
            throw validation("invalid_hex", field + " must be hexadecimal", Map.of("field", field));
        }
        return text;
    }

    private static String requireOtp(String value) {
        String text = requireText(value, "otp");
        if (!OTP_PATTERN.matcher(text).matches()) {
            throw validation("invalid_otp", "OTP must contain only digits", Map.of("field", "otp"));
        }
        return text;
    }

    private static String requireText(String value, String field) {
        if (value == null) {
            throw validation("invalid_input", field + " is required", Map.of("field", field));
        }
        String text = value.trim();
        if (text.isEmpty()) {
            throw validation("invalid_input", field + " must not be blank", Map.of("field", field));
        }
        return text;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safeDecimal(String value) {
        return value == null ? "" : value.trim();
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
        return spaceIndex > 0 ? trimmed.substring(0, spaceIndex) : trimmed;
    }

    private static Integer valueAsInteger(Map<String, Object> fields, String key) {
        if (fields == null) {
            return null;
        }
        Object value = fields.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static EmvCapReplayValidationException validation(
            String reasonCode, String message, Map<String, Object> details) {
        return new EmvCapReplayValidationException(reasonCode, message, Map.copyOf(details));
    }

    private static EmvCapReplayUnexpectedException unexpected(String message, Map<String, Object> details) {
        return new EmvCapReplayUnexpectedException(message, Map.copyOf(details));
    }
}
