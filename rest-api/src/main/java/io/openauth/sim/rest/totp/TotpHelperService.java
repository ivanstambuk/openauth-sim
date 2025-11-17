package io.openauth.sim.rest.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.totp.TotpCurrentOtpHelperService;
import io.openauth.sim.application.totp.TotpCurrentOtpHelperService.LookupCommand;
import io.openauth.sim.application.totp.TotpCurrentOtpHelperService.LookupResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.rest.EvaluationWindowRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class TotpHelperService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.totp.helper.telemetry");

    private final TotpCurrentOtpHelperService helperService;

    TotpHelperService(TotpCurrentOtpHelperService helperService) {
        this.helperService = helperService;
    }

    TotpHelperResponse currentOtp(TotpHelperRequest request) {
        String credentialId = Optional.ofNullable(request.credentialId())
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .orElseThrow(() -> validation("credential_id_required", "Credential ID is required"));

        TotpDriftWindow window = resolveWindow(request.window());
        Optional<Instant> timestamp = request.timestamp() != null
                ? Optional.of(Instant.ofEpochSecond(request.timestamp()))
                : Optional.empty();
        Optional<Instant> override = request.timestampOverride() != null
                ? Optional.of(Instant.ofEpochSecond(request.timestampOverride()))
                : Optional.empty();

        LookupResult result = helperService.lookup(new LookupCommand(credentialId, window, timestamp, override));
        TelemetrySignal signal = result.evaluationResult().telemetry();
        String telemetryId = nextTelemetryId();
        TelemetryFrame frame = signal.emit(TelemetryContracts.totpHelperAdapter(), telemetryId);
        logTelemetry(frame, credentialId);

        if (signal.status() != TelemetryStatus.SUCCESS) {
            throw validation(
                    signal.reasonCode(),
                    Optional.ofNullable(signal.reason()).orElse(signal.reasonCode()),
                    Map.of("telemetryId", telemetryId));
        }

        String otp = result.evaluationResult().otp();
        if (otp == null || otp.isBlank()) {
            throw unexpected("Helper lookup did not return an OTP", null);
        }

        long generationEpochSeconds = result.generationInstant().getEpochSecond();
        long expiresEpochSeconds = result.expiresAt().getEpochSecond();
        TotpHelperMetadata metadata = buildMetadata(result, frame, telemetryId);

        return new TotpHelperResponse(credentialId, otp, generationEpochSeconds, expiresEpochSeconds, metadata);
    }

    private TotpHelperMetadata buildMetadata(LookupResult result, TelemetryFrame frame, String telemetryId) {
        Map<String, Object> fields = new LinkedHashMap<>(frame.fields());
        String algorithm = result.evaluationResult().algorithm() != null
                ? result.evaluationResult().algorithm().name()
                : fields.getOrDefault("algorithm", "SHA1").toString();
        Integer digits = result.evaluationResult().digits() != null
                ? result.evaluationResult().digits()
                : fields.containsKey("digits")
                        ? Integer.valueOf(fields.get("digits").toString())
                        : null;
        long stepSeconds = result.evaluationResult().stepDuration() != null
                ? result.evaluationResult().stepDuration().getSeconds()
                : fields.containsKey("stepSeconds")
                        ? Long.parseLong(fields.get("stepSeconds").toString())
                        : 30L;
        TotpDriftWindow driftWindow = Optional.ofNullable(
                        result.evaluationResult().driftWindow())
                .orElse(TotpDriftWindow.of(
                        parseIntField(fields, "driftBackwardSteps", 1), parseIntField(fields, "driftForwardSteps", 1)));

        return new TotpHelperMetadata(
                algorithm,
                digits,
                stepSeconds,
                driftWindow.backwardSteps(),
                driftWindow.forwardSteps(),
                result.timestampOverrideProvided(),
                telemetryId,
                frame.fields().getOrDefault("reasonCode", "generated").toString());
    }

    private static int parseIntField(Map<String, Object> fields, String key, int defaultValue) {
        if (!fields.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(fields.get(key).toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static TotpDriftWindow resolveWindow(EvaluationWindowRequest request) {
        if (request == null) {
            return TotpDriftWindow.of(1, 1);
        }
        int backward = request.backwardOrDefault(1);
        int forward = request.forwardOrDefault(1);
        return TotpDriftWindow.of(backward, forward);
    }

    private void logTelemetry(TelemetryFrame frame, String credentialId) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=rest.totp.helper")
                .append(" status=")
                .append(frame.status())
                .append(" credentialId=")
                .append(credentialId);
        frame.fields().forEach((key, value) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.contains("otp")) {
                builder.append(' ').append(key).append('=').append(value);
            }
        });
        TELEMETRY_LOGGER.fine(builder.toString());
    }

    private TotpHelperValidationException validation(String reasonCode, String message) {
        return validation(reasonCode, message, Map.of());
    }

    private TotpHelperValidationException validation(String reasonCode, String message, Map<String, Object> details) {
        return new TotpHelperValidationException(reasonCode, message, details);
    }

    private TotpHelperUnexpectedException unexpected(String message, Throwable cause) {
        return new TotpHelperUnexpectedException(message, cause);
    }

    private static String nextTelemetryId() {
        return "rest-totp-helper-" + UUID.randomUUID();
    }
}
