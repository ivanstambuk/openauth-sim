package io.openauth.sim.rest.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.rest.EvaluationWindowRequest;
import io.openauth.sim.rest.OtpPreviewResponse;
import io.openauth.sim.rest.VerboseTracePayload;
import io.openauth.sim.rest.support.InlineSecretInput;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class TotpEvaluationService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.totp.telemetry");

    private final TotpEvaluationApplicationService applicationService;

    TotpEvaluationService(TotpEvaluationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    TotpEvaluationResponse evaluateStored(TotpStoredEvaluationRequest request) {
        String credentialId = Optional.ofNullable(request.credentialId())
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .orElseThrow(() -> validation("credential_id_required", "Credential ID is required"));
        String otp = Optional.ofNullable(request.otp())
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .orElse("");

        TotpDriftWindow drift = resolveWindow(request.window());
        Instant evaluationInstant = request.timestamp() != null ? Instant.ofEpochSecond(request.timestamp()) : null;
        Optional<Instant> timestampOverride = request.timestampOverride() != null
                ? Optional.of(Instant.ofEpochSecond(request.timestampOverride()))
                : Optional.empty();

        boolean verbose = Boolean.TRUE.equals(request.verbose());
        EvaluationResult result = applicationService.evaluate(
                new EvaluationCommand.Stored(credentialId, otp, drift, evaluationInstant, timestampOverride), verbose);
        return handleResult(result, "stored");
    }

    TotpEvaluationResponse evaluateInline(TotpInlineEvaluationRequest request) {
        String secretHex = InlineSecretInput.resolveHex(
                request.sharedSecretHex(),
                request.sharedSecretBase32(),
                () -> validation(
                        "shared_secret_required", "Shared secret is required", Map.of("field", "sharedSecret"), null),
                () -> validation(
                        "shared_secret_conflict",
                        "Provide either sharedSecretHex or sharedSecretBase32, not both",
                        Map.of("field", "sharedSecret"),
                        null),
                message -> validation(
                        "shared_secret_base32_invalid", message, Map.of("field", "sharedSecretBase32"), null));
        Map<String, String> metadata = sanitizeMetadata(request.metadata());

        TotpHashAlgorithm algorithm = Optional.ofNullable(request.algorithm())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .map(TotpHashAlgorithm::valueOf)
                .orElse(TotpHashAlgorithm.SHA1);
        int digits = Optional.ofNullable(request.digits()).orElse(6);
        long stepSeconds = Optional.ofNullable(request.stepSeconds()).orElse(30L);
        TotpDriftWindow drift = resolveWindow(request.window());
        Instant evaluationInstant = request.timestamp() != null ? Instant.ofEpochSecond(request.timestamp()) : null;
        Optional<Instant> timestampOverride = request.timestampOverride() != null
                ? Optional.of(Instant.ofEpochSecond(request.timestampOverride()))
                : Optional.empty();

        boolean verbose = Boolean.TRUE.equals(request.verbose());
        EvaluationResult result = applicationService.evaluate(
                new EvaluationCommand.Inline(
                        secretHex,
                        algorithm,
                        digits,
                        Duration.ofSeconds(stepSeconds),
                        Optional.ofNullable(request.otp()).map(String::trim).orElse(""),
                        drift,
                        evaluationInstant,
                        timestampOverride),
                verbose);
        if (!metadata.isEmpty()) {
            applyPresetMetadata(metadata, result.telemetry());
        }
        return handleResult(result, "inline");
    }

    private TotpEvaluationResponse handleResult(EvaluationResult result, String credentialSource) {
        TelemetrySignal signal = result.telemetry();
        TelemetryFrame frame = signal.emit(TelemetryContracts.totpEvaluationAdapter(), nextTelemetryId());

        logTelemetry(frame, credentialSource);

        if (signal.status() == TelemetryStatus.SUCCESS) {
            TotpEvaluationMetadata metadata = buildMetadata(frame, credentialSource, result, signal.fields());
            List<OtpPreviewResponse> previews = result.previews().stream()
                    .map(entry -> new OtpPreviewResponse(entry.counter(), entry.delta(), entry.otp()))
                    .toList();
            return new TotpEvaluationResponse(
                    signal.reasonCode(),
                    signal.reasonCode(),
                    result.valid(),
                    result.otp(),
                    previews,
                    metadata,
                    result.verboseTrace().map(VerboseTracePayload::from).orElse(null));
        }

        if (signal.status() == TelemetryStatus.INVALID) {
            Map<String, Object> details = new LinkedHashMap<>(signal.fields());
            details.put("credentialSource", credentialSource);
            details.put("matchedSkewSteps", result.matchedSkewSteps());
            throw validation(
                    signal.reasonCode(),
                    Optional.ofNullable(signal.reason()).orElse(signal.reasonCode()),
                    details,
                    result.verboseTrace().orElse(null));
        }

        throw unexpected(
                "TOTP evaluation failed unexpectedly",
                null,
                result.verboseTrace().orElse(null));
    }

    private TotpEvaluationMetadata buildMetadata(
            TelemetryFrame frame,
            String credentialSource,
            EvaluationResult result,
            Map<String, Object> telemetryFields) {
        Map<String, Object> fields = new LinkedHashMap<>(frame.fields());
        fields.putAll(telemetryFields);

        String telemetryId = String.valueOf(fields.getOrDefault("telemetryId", nextTelemetryId()));
        String algorithm = String.valueOf(fields.getOrDefault("algorithm", "SHA1"));
        Integer digits = fields.containsKey("digits")
                ? Integer.valueOf(fields.get("digits").toString())
                : null;
        long stepSeconds = fields.containsKey("stepSeconds")
                ? Long.parseLong(fields.get("stepSeconds").toString())
                : (result.stepDuration() != null ? result.stepDuration().getSeconds() : 30L);
        int driftBackward = fields.containsKey("driftBackwardSteps")
                ? Integer.parseInt(fields.get("driftBackwardSteps").toString())
                : (result.driftWindow() != null ? result.driftWindow().backwardSteps() : 0);
        int driftForward = fields.containsKey("driftForwardSteps")
                ? Integer.parseInt(fields.get("driftForwardSteps").toString())
                : (result.driftWindow() != null ? result.driftWindow().forwardSteps() : 0);
        boolean timestampOverrideProvided =
                Boolean.parseBoolean(String.valueOf(fields.getOrDefault("timestampOverrideProvided", Boolean.FALSE)));

        return new TotpEvaluationMetadata(
                credentialSource,
                result.matchedSkewSteps(),
                algorithm,
                digits,
                stepSeconds,
                driftBackward,
                driftForward,
                timestampOverrideProvided,
                telemetryId);
    }

    private static void logTelemetry(TelemetryFrame frame, String credentialSource) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=rest.totp.evaluate")
                .append(" status=")
                .append(frame.status())
                .append(" credentialSource=")
                .append(credentialSource);
        frame.fields().forEach((key, value) -> {
            if ("telemetryId".equals(key) || key.toLowerCase(Locale.ROOT).contains("secret")) {
                builder.append(' ').append(key).append('=').append(value);
            } else if (!key.toLowerCase(Locale.ROOT).contains("otp")) {
                builder.append(' ').append(key).append('=').append(value);
            }
        });
        TELEMETRY_LOGGER.fine(builder.toString());
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String trimmedKey = key.trim();
            String trimmedValue = value.trim();
            if (!trimmedKey.isEmpty() && !trimmedValue.isEmpty()) {
                sanitized.put(trimmedKey, trimmedValue);
            }
        });
        return sanitized.isEmpty() ? Map.of() : sanitized;
    }

    private static TotpDriftWindow resolveWindow(EvaluationWindowRequest request) {
        if (request == null) {
            return TotpDriftWindow.of(1, 1);
        }
        int backward = request.backwardOrDefault(1);
        int forward = request.forwardOrDefault(1);
        return TotpDriftWindow.of(backward, forward);
    }

    private void applyPresetMetadata(Map<String, String> metadata, TelemetrySignal signal) {
        if (metadata.isEmpty() || signal == null) {
            return;
        }
        Map<String, Object> fields = signal.fields();
        if (fields == null) {
            return;
        }
        String presetKey = metadata.get("presetKey");
        if (presetKey != null && !presetKey.isBlank()) {
            fields.putIfAbsent("samplePresetKey", presetKey);
        }
        String presetLabel = metadata.get("presetLabel");
        if (presetLabel != null && !presetLabel.isBlank()) {
            fields.putIfAbsent("samplePresetLabel", presetLabel);
        }
    }

    private TotpEvaluationValidationException validation(String reasonCode, String message) {
        return validation(reasonCode, message, Map.of(), null);
    }

    private TotpEvaluationValidationException validation(
            String reasonCode, String message, Map<String, Object> details, VerboseTrace trace) {
        return new TotpEvaluationValidationException(reasonCode, message, details, trace);
    }

    private TotpEvaluationUnexpectedException unexpected(String message, Throwable cause, VerboseTrace trace) {
        return new TotpEvaluationUnexpectedException(message, cause, trace);
    }

    private static String nextTelemetryId() {
        return "rest-totp-" + UUID.randomUUID();
    }
}
