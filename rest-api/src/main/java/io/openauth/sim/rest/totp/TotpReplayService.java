package io.openauth.sim.rest.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.totp.TotpReplayApplicationService.ReplayResult;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class TotpReplayService {

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.totp.telemetry");

  private final TotpReplayApplicationService applicationService;

  TotpReplayService(TotpReplayApplicationService applicationService) {
    this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
  }

  TotpReplayResponse replay(TotpReplayRequest request) {
    Objects.requireNonNull(request, "request");
    String telemetryId = nextTelemetryId();
    Mode mode = determineMode(request, telemetryId);

    ReplayCommand command = buildCommand(request, mode, telemetryId);
    ReplayResult result = applicationService.replay(command);
    TelemetrySignal signal = result.telemetry();

    TelemetryFrame frame = signal.emit(TelemetryContracts.totpReplayAdapter(), telemetryId);
    String resolvedTelemetryId =
        String.valueOf(frame.fields().getOrDefault("telemetryId", telemetryId));
    logTelemetry(frame, mode.source);

    TotpReplayMetadata metadata =
        buildMetadata(result, mode.source, resolvedTelemetryId, frame.fields());

    return switch (signal.status()) {
      case SUCCESS -> new TotpReplayResponse("match", signal.reasonCode(), metadata);
      case INVALID -> handleInvalid(request, signal, metadata, resolvedTelemetryId, mode, frame);
      case ERROR ->
          throw unexpected(
              resolvedTelemetryId,
              mode.source,
              safeMessage(signal.reason()),
              sanitizedDetails(frame.fields(), resolvedTelemetryId, mode.source),
              null);
    };
  }

  private TotpReplayResponse handleInvalid(
      TotpReplayRequest request,
      TelemetrySignal signal,
      TotpReplayMetadata metadata,
      String telemetryId,
      Mode mode,
      TelemetryFrame frame) {
    if ("otp_out_of_window".equals(signal.reasonCode())) {
      return new TotpReplayResponse("mismatch", signal.reasonCode(), metadata);
    }

    Map<String, Object> details = sanitizedDetails(frame.fields(), telemetryId, mode.source);
    if (metadata.credentialId() != null) {
      details.putIfAbsent("credentialId", metadata.credentialId());
    }
    if (request.credentialId() != null) {
      details.putIfAbsent("requestCredentialId", request.credentialId());
    }

    throw validation(
        telemetryId,
        mode.source,
        signal.reasonCode(),
        safeMessage(signal.reason()),
        signal.sanitized(),
        details);
  }

  private ReplayCommand buildCommand(TotpReplayRequest request, Mode mode, String telemetryId) {
    return switch (mode) {
      case STORED -> buildStoredCommand(request, telemetryId, mode.source);
      case INLINE -> buildInlineCommand(request, telemetryId, mode.source);
    };
  }

  private ReplayCommand buildStoredCommand(
      TotpReplayRequest request, String telemetryId, String credentialSource) {
    String credentialId =
        requireText(
            request.credentialId(),
            "credential_id_required",
            "Credential ID is required",
            telemetryId,
            credentialSource,
            Map.of("field", "credentialId"));
    String otp =
        requireText(
            request.otp(),
            "otp_required",
            "OTP is required",
            telemetryId,
            credentialSource,
            Map.of("field", "otp"));

    TotpDriftWindow drift = buildDriftWindow(request, telemetryId, credentialSource);
    Instant evaluationInstant =
        toInstant(request.timestamp(), "timestamp", telemetryId, credentialSource);
    Optional<Instant> timestampOverride =
        Optional.ofNullable(
            toInstant(
                request.timestampOverride(), "timestampOverride", telemetryId, credentialSource));

    return new ReplayCommand.Stored(credentialId, otp, drift, evaluationInstant, timestampOverride);
  }

  private ReplayCommand buildInlineCommand(
      TotpReplayRequest request, String telemetryId, String credentialSource) {
    String secretHex =
        requireText(
            request.sharedSecretHex(),
            "shared_secret_required",
            "Shared secret is required",
            telemetryId,
            credentialSource,
            Map.of("field", "sharedSecretHex"));
    String otp =
        requireText(
            request.otp(),
            "otp_required",
            "OTP is required",
            telemetryId,
            credentialSource,
            Map.of("field", "otp"));

    TotpHashAlgorithm algorithm =
        parseAlgorithm(request.algorithm(), telemetryId, credentialSource);

    int digits =
        request.digits() != null
            ? requirePositive(
                request.digits(),
                "digits_invalid",
                "Digits must be positive",
                telemetryId,
                credentialSource)
            : 6;

    long stepSeconds =
        request.stepSeconds() != null
            ? requirePositive(
                request.stepSeconds(),
                "step_seconds_invalid",
                "Step duration must be positive",
                telemetryId,
                credentialSource)
            : 30L;

    TotpDriftWindow drift = buildDriftWindow(request, telemetryId, credentialSource);
    Instant evaluationInstant =
        toInstant(request.timestamp(), "timestamp", telemetryId, credentialSource);
    Optional<Instant> timestampOverride =
        Optional.ofNullable(
            toInstant(
                request.timestampOverride(), "timestampOverride", telemetryId, credentialSource));

    return new ReplayCommand.Inline(
        secretHex,
        algorithm,
        digits,
        Duration.ofSeconds(stepSeconds),
        otp,
        drift,
        evaluationInstant,
        timestampOverride);
  }

  private Mode determineMode(TotpReplayRequest request, String telemetryId) {
    boolean hasCredentialId = hasText(request.credentialId());
    boolean hasSecret = hasText(request.sharedSecretHex());
    boolean hasInlineHints =
        hasSecret
            || request.algorithm() != null
            || request.digits() != null
            || request.stepSeconds() != null;

    if (hasCredentialId && !hasInlineHints) {
      return Mode.STORED;
    }
    if (!hasCredentialId && hasInlineHints) {
      return Mode.INLINE;
    }
    if (hasCredentialId && hasInlineHints) {
      throw validation(
          telemetryId,
          "ambiguous",
          "mode_ambiguous",
          "Request mixes stored and inline replay fields",
          true,
          Map.of("credentialId", request.credentialId()));
    }
    throw validation(
        telemetryId,
        "unknown",
        "mode_unspecified",
        "Provide either credentialId for stored replay or inline parameters",
        true,
        Map.of());
  }

  private TotpDriftWindow buildDriftWindow(
      TotpReplayRequest request, String telemetryId, String credentialSource) {
    int backward = Optional.ofNullable(request.driftBackward()).orElse(1);
    int forward = Optional.ofNullable(request.driftForward()).orElse(1);
    try {
      return TotpDriftWindow.of(backward, forward);
    } catch (IllegalArgumentException ex) {
      throw validation(
          telemetryId,
          credentialSource,
          "drift_invalid",
          ex.getMessage(),
          true,
          Map.of("driftBackward", backward, "driftForward", forward));
    }
  }

  private TotpReplayMetadata buildMetadata(
      ReplayResult result,
      String credentialSource,
      String telemetryId,
      Map<String, Object> telemetryFields) {

    TotpDriftWindow driftWindow = result.driftWindow();
    String algorithm =
        result.algorithm() != null
            ? result.algorithm().name()
            : valueAsString(telemetryFields, "algorithm");
    Integer digits =
        result.digits() != null ? result.digits() : valueAsInteger(telemetryFields, "digits");
    Long stepSeconds =
        result.stepDuration() != null
            ? result.stepDuration().getSeconds()
            : valueAsLong(telemetryFields, "stepSeconds");
    Integer driftBackward =
        driftWindow != null
            ? driftWindow.backwardSteps()
            : valueAsInteger(telemetryFields, "driftBackwardSteps");
    Integer driftForward =
        driftWindow != null
            ? driftWindow.forwardSteps()
            : valueAsInteger(telemetryFields, "driftForwardSteps");

    return new TotpReplayMetadata(
        credentialSource,
        result.credentialId(),
        result.credentialReference(),
        algorithm,
        digits,
        stepSeconds,
        driftBackward,
        driftForward,
        result.matchedSkewSteps(),
        result.timestampOverrideProvided(),
        telemetryId);
  }

  private static String valueAsString(Map<String, Object> fields, String key) {
    if (fields == null) {
      return null;
    }
    Object value = fields.get(key);
    return value != null ? value.toString() : null;
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

  private static Long valueAsLong(Map<String, Object> fields, String key) {
    if (fields == null) {
      return null;
    }
    Object value = fields.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value != null) {
      try {
        return Long.parseLong(value.toString());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private TotpHashAlgorithm parseAlgorithm(
      String algorithm, String telemetryId, String credentialSource) {
    if (algorithm == null || algorithm.isBlank()) {
      return TotpHashAlgorithm.SHA1;
    }
    try {
      return TotpHashAlgorithm.valueOf(algorithm.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw validation(
          telemetryId,
          credentialSource,
          "algorithm_invalid",
          ex.getMessage(),
          true,
          Map.of("algorithm", algorithm));
    }
  }

  private Instant toInstant(
      Long epochSeconds, String field, String telemetryId, String credentialSource) {
    if (epochSeconds == null) {
      return null;
    }
    try {
      return Instant.ofEpochSecond(epochSeconds);
    } catch (DateTimeException ex) {
      throw validation(
          telemetryId,
          credentialSource,
          field + "_invalid",
          ex.getMessage(),
          true,
          Map.of(field, epochSeconds));
    }
  }

  private static String safeMessage(String message) {
    return (message == null || message.isBlank()) ? "Replay request failed" : message;
  }

  private static void logTelemetry(TelemetryFrame frame, String credentialSource) {
    if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
      return;
    }
    StringBuilder builder =
        new StringBuilder("event=rest.totp.replay")
            .append(" status=")
            .append(frame.status())
            .append(" credentialSource=")
            .append(credentialSource);

    frame
        .fields()
        .forEach(
            (key, value) -> {
              String lower = key.toLowerCase(Locale.ROOT);
              if ("telemetryid".equals(lower)
                  || (!lower.contains("otp") && !lower.contains("secret"))) {
                builder.append(' ').append(key).append('=').append(value);
              }
            });

    TELEMETRY_LOGGER.fine(builder.toString());
  }

  private TotpReplayValidationException validation(
      String telemetryId,
      String credentialSource,
      String reasonCode,
      String message,
      boolean sanitized,
      Map<String, Object> details) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("telemetryId", telemetryId);
    payload.put("credentialSource", credentialSource);
    if (details != null && !details.isEmpty()) {
      payload.putAll(details);
    }
    return new TotpReplayValidationException(
        telemetryId, credentialSource, reasonCode, message, sanitized, payload);
  }

  private TotpReplayUnexpectedException unexpected(
      String telemetryId,
      String credentialSource,
      String message,
      Map<String, Object> details,
      Throwable cause) {
    return new TotpReplayUnexpectedException(
        telemetryId, credentialSource, message, details, cause);
  }

  private static Map<String, Object> sanitizedDetails(
      Map<String, Object> fields, String telemetryId, String credentialSource) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("telemetryId", telemetryId);
    details.put("credentialSource", credentialSource);
    if (fields != null) {
      fields.forEach(
          (key, value) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if ("telemetryid".equals(lower) || lower.contains("otp") || lower.contains("secret")) {
              return;
            }
            details.putIfAbsent(key, value);
          });
    }
    return details;
  }

  private String requireText(
      String value,
      String reasonCode,
      String message,
      String telemetryId,
      String credentialSource,
      Map<String, Object> detailOverrides) {
    if (hasText(value)) {
      return value.trim();
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("telemetryId", telemetryId);
    details.put("credentialSource", credentialSource);
    if (detailOverrides != null) {
      details.putAll(detailOverrides);
    }
    throw validation(telemetryId, credentialSource, reasonCode, message, true, details);
  }

  private int requirePositive(
      Number value,
      String reasonCode,
      String message,
      String telemetryId,
      String credentialSource) {
    if (value.longValue() > 0) {
      return value.intValue();
    }
    throw validation(
        telemetryId, credentialSource, reasonCode, message, true, Map.of("value", value));
  }

  private String nextTelemetryId() {
    return "rest-totp-" + UUID.randomUUID();
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
