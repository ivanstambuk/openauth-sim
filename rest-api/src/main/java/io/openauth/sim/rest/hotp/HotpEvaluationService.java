package io.openauth.sim.rest.hotp;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/** REST-facing orchestration for HOTP evaluation requests. */
@Service
class HotpEvaluationService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.hotp.telemetry");

    static {
        TELEMETRY_LOGGER.setLevel(Level.ALL);
    }

    private final HotpEvaluationApplicationService applicationService;
    private final HotpTelemetryAdapter telemetryAdapter = TelemetryContracts.hotpEvaluationAdapter();

    HotpEvaluationService(HotpEvaluationApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    }

    HotpEvaluationResponse evaluateStored(HotpStoredEvaluationRequest request) {
        Objects.requireNonNull(request, "request");
        String telemetryId = nextTelemetryId();

        String credentialId = requireText(request.credentialId(), "credentialId", telemetryId, Mode.STORED, null);
        EvaluationCommand command = new EvaluationCommand.Stored(credentialId);
        boolean verbose = Boolean.TRUE.equals(request.verbose());
        EvaluationResult result = applicationService.evaluate(command, verbose);
        return handleResult(result, Mode.STORED, telemetryId, credentialId, Map.of("credentialId", credentialId));
    }

    HotpEvaluationResponse evaluateInline(HotpInlineEvaluationRequest request) {
        Objects.requireNonNull(request, "request");
        String telemetryId = nextTelemetryId();

        String secretHex = requireText(
                request.sharedSecretHex(),
                "sharedSecretHex",
                telemetryId,
                Mode.INLINE,
                Map.of("field", "sharedSecretHex"));
        HotpHashAlgorithm algorithm = parseAlgorithm(request.algorithm(), telemetryId);
        int digits = requireDigits(request.digits(), telemetryId);
        long counter = requireCounter(request.counter(), telemetryId);
        Map<String, String> metadata = request.metadata() == null ? Map.of() : Map.copyOf(request.metadata());

        EvaluationCommand command = new EvaluationCommand.Inline(secretHex, algorithm, digits, counter, metadata);
        boolean verbose = Boolean.TRUE.equals(request.verbose());
        EvaluationResult result = applicationService.evaluate(command, verbose);
        return handleResult(result, Mode.INLINE, telemetryId, null, Map.of());
    }

    private HotpEvaluationResponse handleResult(
            EvaluationResult result,
            Mode mode,
            String telemetryId,
            String identifier,
            Map<String, String> contextDetails) {

        TelemetrySignal signal = result.telemetry();

        TelemetryFrame frame = signal.emit(telemetryAdapter, telemetryId);
        logTelemetry(levelFor(signal.status()), frame);

        HotpEvaluationMetadata metadata = new HotpEvaluationMetadata(
                mode.source,
                result.credentialId(),
                result.credentialReference(),
                result.algorithm() != null ? result.algorithm().name() : null,
                result.digits(),
                result.previousCounter(),
                result.nextCounter(),
                result.samplePresetKey(),
                result.samplePresetLabel(),
                telemetryId);

        return switch (signal.status()) {
            case SUCCESS ->
                new HotpEvaluationResponse(
                        "generated",
                        signal.reasonCode(),
                        result.otp(),
                        metadata,
                        result.verboseTrace().map(VerboseTracePayload::from).orElse(null));
            case INVALID ->
                handleInvalid(
                        signal,
                        telemetryId,
                        mode,
                        identifier,
                        frame.fields(),
                        contextDetails,
                        result.verboseTrace().orElse(null));
            case ERROR ->
                throw unexpectedError(
                        signal,
                        telemetryId,
                        mode,
                        frame.fields(),
                        result.verboseTrace().orElse(null));
        };
    }

    private HotpEvaluationResponse handleInvalid(
            TelemetrySignal signal,
            String telemetryId,
            Mode mode,
            String identifier,
            Map<String, Object> fields,
            Map<String, String> contextDetails,
            VerboseTrace trace) {

        Map<String, Object> details =
                sanitizedDetails(fields, contextDetails, signal.sanitized(), telemetryId, mode.source);

        throw new HotpEvaluationValidationException(
                telemetryId,
                mode.source,
                identifier,
                signal.reasonCode(),
                signal.sanitized(),
                details,
                safeMessage(signal.reason()),
                trace);
    }

    private RuntimeException unexpectedError(
            TelemetrySignal signal, String telemetryId, Mode mode, Map<String, Object> fields, VerboseTrace trace) {
        Map<String, Object> details = sanitizedDetails(fields, Map.of(), false, telemetryId, mode.source);
        return new HotpEvaluationUnexpectedException(
                telemetryId, mode.source, safeMessage(signal.reason()), details, trace);
    }

    private Map<String, Object> sanitizedDetails(
            Map<String, Object> telemetryFields,
            Map<String, String> base,
            boolean sanitized,
            String telemetryId,
            String source) {

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("telemetryId", telemetryId);
        details.put("credentialSource", source);
        details.put("sanitized", sanitized);
        if (base != null) {
            base.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    details.putIfAbsent(key, value);
                }
            });
        }

        if (telemetryFields != null) {
            telemetryFields.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                String lower = key.toLowerCase(Locale.ROOT);
                if (lower.contains("secret")) {
                    return;
                }
                if (!sanitized && (lower.contains("otp") || lower.contains("code"))) {
                    return;
                }
                details.putIfAbsent(key, value);
            });
        }
        return details;
    }

    private Level levelFor(TelemetryStatus status) {
        return switch (status) {
            case SUCCESS -> Level.INFO;
            case INVALID -> Level.WARNING;
            case ERROR -> Level.SEVERE;
        };
    }

    private String requireText(
            String value, String field, String telemetryId, Mode mode, Map<String, String> additionalDetails) {
        if (value == null || value.trim().isEmpty()) {
            Map<String, String> details =
                    additionalDetails == null ? Map.of("field", field) : mergeDetails(additionalDetails, field);
            throw validationFailure(
                    telemetryId,
                    mode,
                    details.getOrDefault("credentialId", details.get("identifier")),
                    field + " is required",
                    field + "_required",
                    details);
        }
        return value.trim();
    }

    private int requireDigits(Integer digits, String telemetryId) {
        if (digits == null) {
            throw validationFailure(
                    telemetryId, Mode.INLINE, null, "digits is required", "digits_required", Map.of("field", "digits"));
        }
        return digits;
    }

    private long requireCounter(Long counter, String telemetryId) {
        if (counter == null) {
            throw validationFailure(
                    telemetryId,
                    Mode.INLINE,
                    null,
                    "counter is required",
                    "counter_required",
                    Map.of("field", "counter"));
        }
        return counter;
    }

    private HotpHashAlgorithm parseAlgorithm(String algorithm, String telemetryId) {
        String normalized = requireText(algorithm, "algorithm", telemetryId, Mode.INLINE, Map.of("field", "algorithm"))
                .toUpperCase(Locale.ROOT);
        try {
            return HotpHashAlgorithm.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw validationFailure(
                    telemetryId,
                    Mode.INLINE,
                    null,
                    "Unsupported HOTP algorithm: " + normalized,
                    "algorithm_invalid",
                    Map.of("field", "algorithm"));
        }
    }

    private HotpEvaluationValidationException validationFailure(
            String telemetryId,
            Mode mode,
            String credentialReference,
            String message,
            String reasonCode,
            Map<String, String> baseDetails) {

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("credentialSource", mode.source);
        if (credentialReference != null && !credentialReference.isBlank()) {
            telemetryFields.put(mode == Mode.STORED ? "credentialId" : "identifier", credentialReference);
        }
        baseDetails.forEach(telemetryFields::putIfAbsent);

        TelemetryFrame frame =
                telemetryAdapter.validationFailure(telemetryId, reasonCode, message, true, telemetryFields);
        logTelemetry(Level.WARNING, frame);

        Map<String, Object> details = sanitizedDetails(telemetryFields, baseDetails, true, telemetryId, mode.source);

        return new HotpEvaluationValidationException(
                telemetryId, mode.source, credentialReference, reasonCode, true, details, message, null);
    }

    private static Map<String, String> mergeDetails(Map<String, String> original, String field) {
        Map<String, String> merged = new LinkedHashMap<>(original);
        merged.put("field", field);
        return merged;
    }

    private static String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().replaceAll("\\s+", " ");
    }

    private void logTelemetry(Level level, TelemetryFrame frame) {
        StringBuilder builder = new StringBuilder("event=rest.")
                .append(frame.event())
                .append(" status=")
                .append(frame.status());

        frame.fields()
                .forEach((key, value) ->
                        builder.append(' ').append(key).append('=').append(value));

        LogRecord record = new LogRecord(level, builder.toString());
        TELEMETRY_LOGGER.log(record);
        for (Handler handler : TELEMETRY_LOGGER.getHandlers()) {
            handler.publish(record);
            handler.flush();
        }
    }

    private String nextTelemetryId() {
        return "rest-hotp-" + UUID.randomUUID();
    }

    private enum Mode {
        STORED("stored"),
        INLINE("inline");

        private final String source;

        Mode(String source) {
            this.source = source;
        }
    }
}
