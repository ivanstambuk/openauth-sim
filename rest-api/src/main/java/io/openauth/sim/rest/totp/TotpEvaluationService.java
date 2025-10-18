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
import java.time.Duration;
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
class TotpEvaluationService {

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.totp.telemetry");

  private final TotpEvaluationApplicationService applicationService;

  TotpEvaluationService(TotpEvaluationApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  TotpEvaluationResponse evaluateStored(TotpStoredEvaluationRequest request) {
    String credentialId =
        Optional.ofNullable(request.credentialId())
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .orElseThrow(() -> validation("credential_id_required", "Credential ID is required"));
    String otp =
        Optional.ofNullable(request.otp())
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .orElse("");

    TotpDriftWindow drift =
        TotpDriftWindow.of(
            Optional.ofNullable(request.driftBackward()).orElse(1),
            Optional.ofNullable(request.driftForward()).orElse(1));
    Instant evaluationInstant =
        request.timestamp() != null ? Instant.ofEpochSecond(request.timestamp()) : null;
    Optional<Instant> timestampOverride =
        request.timestampOverride() != null
            ? Optional.of(Instant.ofEpochSecond(request.timestampOverride()))
            : Optional.empty();

    EvaluationResult result =
        applicationService.evaluate(
            new EvaluationCommand.Stored(
                credentialId, otp, drift, evaluationInstant, timestampOverride));
    return handleResult(result, "stored");
  }

  TotpEvaluationResponse evaluateInline(TotpInlineEvaluationRequest request) {
    String secretHex =
        Optional.ofNullable(request.sharedSecretHex())
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseThrow(() -> validation("shared_secret_required", "Shared secret is required"));
    Map<String, String> metadata = sanitizeMetadata(request.metadata());

    TotpHashAlgorithm algorithm =
        Optional.ofNullable(request.algorithm())
            .map(value -> value.toUpperCase(Locale.ROOT))
            .map(TotpHashAlgorithm::valueOf)
            .orElse(TotpHashAlgorithm.SHA1);
    int digits = Optional.ofNullable(request.digits()).orElse(6);
    long stepSeconds = Optional.ofNullable(request.stepSeconds()).orElse(30L);
    TotpDriftWindow drift =
        TotpDriftWindow.of(
            Optional.ofNullable(request.driftBackward()).orElse(1),
            Optional.ofNullable(request.driftForward()).orElse(1));
    Instant evaluationInstant =
        request.timestamp() != null ? Instant.ofEpochSecond(request.timestamp()) : null;
    Optional<Instant> timestampOverride =
        request.timestampOverride() != null
            ? Optional.of(Instant.ofEpochSecond(request.timestampOverride()))
            : Optional.empty();

    EvaluationResult result =
        applicationService.evaluate(
            new EvaluationCommand.Inline(
                secretHex,
                algorithm,
                digits,
                Duration.ofSeconds(stepSeconds),
                Optional.ofNullable(request.otp()).map(String::trim).orElse(""),
                drift,
                evaluationInstant,
                timestampOverride));
    if (!metadata.isEmpty()) {
      applyPresetMetadata(metadata, result.telemetry());
    }
    return handleResult(result, "inline");
  }

  private TotpEvaluationResponse handleResult(EvaluationResult result, String credentialSource) {
    TelemetrySignal signal = result.telemetry();
    TelemetryFrame frame =
        signal.emit(TelemetryContracts.totpEvaluationAdapter(), nextTelemetryId());

    logTelemetry(frame, credentialSource);

    if (signal.status() == TelemetryStatus.SUCCESS) {
      TotpEvaluationMetadata metadata =
          buildMetadata(frame, credentialSource, result, signal.fields());
      return new TotpEvaluationResponse(
          signal.reasonCode(), signal.reasonCode(), result.valid(), result.otp(), metadata);
    }

    if (signal.status() == TelemetryStatus.INVALID) {
      Map<String, Object> details = new LinkedHashMap<>(signal.fields());
      details.put("credentialSource", credentialSource);
      details.put("matchedSkewSteps", result.matchedSkewSteps());
      throw validation(
          signal.reasonCode(),
          Optional.ofNullable(signal.reason()).orElse(signal.reasonCode()),
          details);
    }

    throw unexpected("TOTP evaluation failed unexpectedly", null);
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
    Integer digits =
        fields.containsKey("digits") ? Integer.valueOf(fields.get("digits").toString()) : null;
    long stepSeconds =
        fields.containsKey("stepSeconds")
            ? Long.parseLong(fields.get("stepSeconds").toString())
            : (result.stepDuration() != null ? result.stepDuration().getSeconds() : 30L);
    int driftBackward =
        fields.containsKey("driftBackwardSteps")
            ? Integer.parseInt(fields.get("driftBackwardSteps").toString())
            : (result.driftWindow() != null ? result.driftWindow().backwardSteps() : 0);
    int driftForward =
        fields.containsKey("driftForwardSteps")
            ? Integer.parseInt(fields.get("driftForwardSteps").toString())
            : (result.driftWindow() != null ? result.driftWindow().forwardSteps() : 0);
    boolean timestampOverrideProvided =
        Boolean.parseBoolean(
            String.valueOf(fields.getOrDefault("timestampOverrideProvided", Boolean.FALSE)));

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
    StringBuilder builder =
        new StringBuilder("event=rest.totp.evaluate")
            .append(" status=")
            .append(frame.status())
            .append(" credentialSource=")
            .append(credentialSource);
    frame
        .fields()
        .forEach(
            (key, value) -> {
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
    metadata.forEach(
        (key, value) -> {
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
    return validation(reasonCode, message, Map.of());
  }

  private TotpEvaluationValidationException validation(
      String reasonCode, String message, Map<String, Object> details) {
    return new TotpEvaluationValidationException(reasonCode, message, details);
  }

  private TotpEvaluationUnexpectedException unexpected(String message, Throwable cause) {
    return new TotpEvaluationUnexpectedException(message, cause);
  }

  private static String nextTelemetryId() {
    return "rest-totp-" + UUID.randomUUID();
  }
}
