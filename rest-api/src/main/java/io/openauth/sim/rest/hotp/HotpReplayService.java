package io.openauth.sim.rest.hotp;

import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayResult;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.TelemetrySignal;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
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

/** REST-facing orchestration for HOTP replay requests. */
@Service
class HotpReplayService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.hotp.telemetry");
    private static final String INLINE_REPLAY_ID = "hotp-inline-replay";

    static {
        TELEMETRY_LOGGER.setLevel(Level.ALL);
    }

    private final HotpReplayApplicationService applicationService;
    private final HotpTelemetryAdapter telemetryAdapter = TelemetryContracts.hotpReplayAdapter();

    HotpReplayService(HotpReplayApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    }

    HotpReplayResponse replay(HotpReplayRequest request) {
        Objects.requireNonNull(request, "request");
        String telemetryId = nextTelemetryId();

        Mode mode = determineMode(request, telemetryId);

        return switch (mode) {
            case STORED -> handleStored(request, telemetryId, mode);
            case INLINE -> handleInline(request, telemetryId, mode);
        };
    }

    private HotpReplayResponse handleStored(HotpReplayRequest request, String telemetryId, Mode mode) {
        String credentialId = requireText(request.credentialId(), "credentialId", telemetryId, mode, null);
        String otp = requireText(request.otp(), "otp", telemetryId, mode, Map.of("credentialId", credentialId));

        ensureMetadataAbsent(request.metadata(), telemetryId, mode, credentialId, Map.of("credentialId", credentialId));

        ReplayCommand command = new ReplayCommand.Stored(credentialId, otp);
        Map<String, String> contextDetails = Map.of("credentialId", credentialId);
        return handleResult(command, mode, telemetryId, credentialId, contextDetails);
    }

    private HotpReplayResponse handleInline(HotpReplayRequest request, String telemetryId, Mode mode) {
        Map<String, String> details = Map.of("credentialId", INLINE_REPLAY_ID);
        String secretHex = requireText(request.sharedSecretHex(), "sharedSecretHex", telemetryId, mode, details);
        HotpHashAlgorithm algorithm = parseAlgorithm(request.algorithm(), telemetryId, INLINE_REPLAY_ID, mode);
        int digits = requireDigits(request.digits(), telemetryId, INLINE_REPLAY_ID, mode);
        long counter = requireCounter(request.counter(), telemetryId, INLINE_REPLAY_ID, mode);
        String otp = requireText(request.otp(), "otp", telemetryId, mode, details);
        ensureMetadataAbsent(request.metadata(), telemetryId, mode, INLINE_REPLAY_ID, details);

        ReplayCommand command = new ReplayCommand.Inline(secretHex, algorithm, digits, counter, otp, Map.of());
        return handleResult(command, mode, telemetryId, INLINE_REPLAY_ID, details);
    }

    private HotpReplayResponse handleResult(
            ReplayCommand command,
            Mode mode,
            String telemetryId,
            String identifier,
            Map<String, String> contextDetails) {

        ReplayResult result = applicationService.replay(command);
        TelemetrySignal signal = result.telemetry();

        TelemetryFrame frame = signal.emit(telemetryAdapter, telemetryId);
        logTelemetry(levelFor(signal.status()), frame);

        HotpReplayMetadata metadata = new HotpReplayMetadata(
                mode.source,
                result.credentialId(),
                result.credentialReference(),
                result.algorithm() != null ? result.algorithm().name() : null,
                result.digits(),
                result.previousCounter(),
                result.nextCounter(),
                telemetryId);

        return switch (signal.status()) {
            case SUCCESS -> new HotpReplayResponse("match", signal.reasonCode(), metadata);
            case INVALID ->
                handleInvalid(signal, metadata, telemetryId, mode, identifier, frame.fields(), contextDetails);
            case ERROR -> throw unexpectedError(signal, telemetryId, mode, frame.fields());
        };
    }

    private HotpReplayResponse handleInvalid(
            TelemetrySignal signal,
            HotpReplayMetadata metadata,
            String telemetryId,
            Mode mode,
            String identifier,
            Map<String, Object> fields,
            Map<String, String> contextDetails) {

        if ("otp_mismatch".equals(signal.reasonCode())) {
            return new HotpReplayResponse("mismatch", signal.reasonCode(), metadata);
        }

        Map<String, String> details =
                sanitizedDetails(fields, contextDetails, signal.sanitized(), telemetryId, mode.source);

        throw new HotpReplayValidationException(
                telemetryId,
                mode.source,
                identifier,
                signal.reasonCode(),
                signal.sanitized(),
                details,
                safeMessage(signal.reason()));
    }

    private RuntimeException unexpectedError(
            TelemetrySignal signal, String telemetryId, Mode mode, Map<String, Object> fields) {
        Map<String, String> details = sanitizedDetails(fields, Map.of(), false, telemetryId, mode.source);
        return new HotpReplayUnexpectedException(telemetryId, mode.source, safeMessage(signal.reason()), details);
    }

    private void ensureMetadataAbsent(
            Map<String, String> metadata,
            String telemetryId,
            Mode mode,
            String identifier,
            Map<String, String> contextDetails) {

        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        Map<String, String> details = new LinkedHashMap<>(contextDetails);

        throw new HotpReplayValidationException(
                telemetryId,
                mode.source,
                identifier,
                "metadata_not_supported",
                true,
                details,
                "Replay metadata is not supported for HOTP.");
    }

    private Mode determineMode(HotpReplayRequest request, String telemetryId) {
        boolean hasCredentialId = hasText(request.credentialId());
        boolean hasSecret = hasText(request.sharedSecretHex());
        boolean hasAlgorithm = hasText(request.algorithm());
        boolean hasDigits = request.digits() != null;
        boolean hasCounter = request.counter() != null;
        boolean hasInlineFields = hasSecret || hasAlgorithm || hasDigits || hasCounter;

        if (hasCredentialId && !hasInlineFields) {
            return Mode.STORED;
        }
        if (!hasCredentialId && hasInlineFields) {
            return Mode.INLINE;
        }
        if (hasCredentialId && hasInlineFields) {
            throw validationFailure(
                    telemetryId,
                    "mixed",
                    request.credentialId(),
                    "Request payload mixes stored and inline fields",
                    "mode_ambiguous",
                    Map.of());
        }
        throw validationFailure(
                telemetryId,
                "unknown",
                null,
                "HOTP replay requires stored credential or inline parameters",
                "mode_required",
                Map.of());
    }

    private HotpReplayValidationException validationFailure(
            String telemetryId,
            String credentialSource,
            String identifier,
            String message,
            String reasonCode,
            Map<String, String> baseDetails) {

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("credentialSource", credentialSource);
        if (identifier != null && !identifier.isBlank()) {
            telemetryFields.put("credentialId", identifier);
        }
        baseDetails.forEach(telemetryFields::putIfAbsent);

        TelemetryFrame frame =
                telemetryAdapter.validationFailure(telemetryId, reasonCode, message, true, telemetryFields);
        logTelemetry(Level.WARNING, frame);

        Map<String, String> details =
                sanitizedDetails(telemetryFields, baseDetails, true, telemetryId, credentialSource);

        return new HotpReplayValidationException(
                telemetryId, credentialSource, identifier, reasonCode, true, details, message);
    }

    private Map<String, String> sanitizedDetails(
            Map<String, Object> telemetryFields,
            Map<String, String> base,
            boolean sanitized,
            String telemetryId,
            String source) {

        Map<String, String> details = new LinkedHashMap<>();
        details.put("telemetryId", telemetryId);
        details.put("credentialSource", source);
        details.put("sanitized", Boolean.toString(sanitized));
        if (base != null) {
            base.forEach(details::putIfAbsent);
        }
        if (sanitized && telemetryFields != null) {
            telemetryFields.forEach((key, value) -> {
                if (value != null) {
                    details.putIfAbsent(key, String.valueOf(value));
                }
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
                    mode.source,
                    details.get("credentialId"),
                    field + " is required",
                    field + "_required",
                    details);
        }
        return value.trim();
    }

    private int requireDigits(Integer digits, String telemetryId, String identifier, Mode mode) {
        if (digits == null) {
            throw validationFailure(
                    telemetryId,
                    mode.source,
                    identifier,
                    "digits is required",
                    "digits_required",
                    Map.of("field", "digits", "credentialId", identifier));
        }
        return digits;
    }

    private long requireCounter(Long counter, String telemetryId, String identifier, Mode mode) {
        if (counter == null) {
            throw validationFailure(
                    telemetryId,
                    mode.source,
                    identifier,
                    "counter is required",
                    "counter_required",
                    Map.of("field", "counter", "credentialId", identifier));
        }
        return counter;
    }

    private HotpHashAlgorithm parseAlgorithm(String algorithm, String telemetryId, String identifier, Mode mode) {
        String normalized = requireText(algorithm, "algorithm", telemetryId, mode, Map.of("credentialId", identifier))
                .toUpperCase(Locale.ROOT);
        try {
            return HotpHashAlgorithm.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw validationFailure(
                    telemetryId,
                    mode.source,
                    identifier,
                    "Unsupported HOTP algorithm: " + normalized,
                    "algorithm_invalid",
                    Map.of("field", "algorithm", "credentialId", identifier));
        }
    }

    private static Map<String, String> mergeDetails(Map<String, String> original, String field) {
        Map<String, String> merged = new LinkedHashMap<>(original);
        merged.put("field", field);
        return merged;
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

    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().replaceAll("\\s+", " ");
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
