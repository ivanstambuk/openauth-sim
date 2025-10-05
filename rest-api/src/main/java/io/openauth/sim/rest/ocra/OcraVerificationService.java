package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraInlineIdentifiers;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.NormalizedRequest;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationCommand;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationContext;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationReason;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationResult;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationStatus;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationValidationException;
import io.openauth.sim.application.ocra.OcraVerificationRequests;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class OcraVerificationService {

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  static {
    TELEMETRY_LOGGER.setLevel(Level.ALL);
  }

  private final OcraVerificationApplicationService applicationService;

  OcraVerificationService(OcraVerificationApplicationService applicationService) {
    this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
  }

  OcraVerificationResponse verify(
      OcraVerificationRequest rawRequest, OcraVerificationAuditContext auditContext) {
    Objects.requireNonNull(rawRequest, "request");
    Objects.requireNonNull(auditContext, "auditContext");

    long started = System.nanoTime();
    String telemetryId = nextTelemetryId();
    CommandEnvelope envelope = null;

    try {
      envelope = CommandEnvelope.from(rawRequest);
      VerificationResult result = applicationService.verify(envelope.command());
      long durationMillis = toMillis(started);
      VerificationLogPayload payload = null;

      switch (result.status()) {
        case MATCH -> {
          payload = verificationPayloadForResult(result, envelope, "match", durationMillis);
          TelemetryFrame frame =
              TelemetryContracts.ocraVerificationAdapter()
                  .status(
                      "match",
                      telemetryId,
                      reasonCodeFor(result.reason()),
                      true,
                      null,
                      payload.fields());
          logVerification(Level.INFO, 200, auditContext, frame);
        }
        case MISMATCH -> {
          payload = verificationPayloadForResult(result, envelope, "mismatch", durationMillis);
          TelemetryFrame frame =
              TelemetryContracts.ocraVerificationAdapter()
                  .status(
                      "mismatch",
                      telemetryId,
                      reasonCodeFor(result.reason()),
                      true,
                      null,
                      payload.fields());
          logVerification(Level.INFO, 200, auditContext, frame);
        }
        case INVALID ->
            handleInvalid(result, envelope, rawRequest, auditContext, telemetryId, durationMillis);
      }

      if (result.status() == VerificationStatus.INVALID) {
        throw new IllegalStateException("Verification invalid state should have been handled");
      }

      Objects.requireNonNull(payload, "verification payload");
      String mode = normalizedMode(envelope.credentialSource());
      OcraVerificationMetadata metadata =
          new OcraVerificationMetadata(
              envelope.credentialSource(),
              mode,
              result.suite(),
              result.responseDigits(),
              durationMillis,
              (String) payload.fields().getOrDefault("contextFingerprint", "unavailable"),
              telemetryId,
              (String) payload.fields().getOrDefault("outcome", "unknown"));

      String status = result.status() == VerificationStatus.MATCH ? "match" : "mismatch";
      return new OcraVerificationResponse(status, reasonCodeFor(result.reason()), metadata);
    } catch (VerificationValidationException ex) {
      long durationMillis = toMillis(started);
      VerificationLogPayload payload =
          verificationPayloadForFailure(envelope, rawRequest, "invalid", durationMillis);

      TelemetryFrame frame =
          TelemetryContracts.ocraVerificationAdapter()
              .validationFailure(
                  telemetryId, ex.reasonCode(), ex.getMessage(), ex.sanitized(), payload.fields());

      logVerification(Level.WARNING, 422, auditContext, frame);

      throw new OcraVerificationValidationException(
          telemetryId,
          payload.suite(),
          ex.field(),
          ex.reasonCode(),
          ex.getMessage(),
          ex.sanitized(),
          ex);
    } catch (ValidationError ex) {
      long durationMillis = toMillis(started);
      VerificationLogPayload payload =
          verificationPayloadForFailure(envelope, rawRequest, "invalid", durationMillis);

      TelemetryFrame frame =
          TelemetryContracts.ocraVerificationAdapter()
              .validationFailure(
                  telemetryId, ex.reasonCode(), ex.getMessage(), true, payload.fields());

      logVerification(Level.WARNING, 422, auditContext, frame);

      throw new OcraVerificationValidationException(
          telemetryId, payload.suite(), ex.field(), ex.reasonCode(), ex.getMessage(), true, ex);
    } catch (RuntimeException ex) {
      long durationMillis = toMillis(started);
      VerificationLogPayload payload =
          verificationPayloadForFailure(envelope, rawRequest, "error", durationMillis);

      TelemetryFrame frame =
          TelemetryContracts.ocraVerificationAdapter()
              .error(telemetryId, "unexpected_error", ex.getMessage(), true, payload.fields());

      logVerification(Level.SEVERE, 500, auditContext, frame);
      throw ex;
    }
  }

  private void handleInvalid(
      VerificationResult result,
      CommandEnvelope envelope,
      OcraVerificationRequest rawRequest,
      OcraVerificationAuditContext auditContext,
      String telemetryId,
      long durationMillis) {

    VerificationReason reason = result.reason();
    String outcome =
        switch (reason) {
          case VALIDATION_FAILURE, CREDENTIAL_NOT_FOUND -> "invalid";
          case UNEXPECTED_ERROR, MATCH, STRICT_MISMATCH -> "error";
        };

    VerificationLogPayload payload =
        verificationPayloadForResult(result, envelope, outcome, durationMillis);

    String status;
    String reasonCode;
    String message;
    int httpStatus;
    Level level;

    switch (reason) {
      case VALIDATION_FAILURE -> {
        status = "invalid";
        reasonCode = "validation_failure";
        message = "Verification inputs failed validation";
        httpStatus = 422;
        level = Level.WARNING;
      }
      case CREDENTIAL_NOT_FOUND -> {
        status = "invalid";
        reasonCode = "credential_not_found";
        message =
            "credentialId %s not found"
                .formatted(Objects.requireNonNullElse(result.credentialId(), "unknown"));
        httpStatus = 404;
        level = Level.WARNING;
      }
      case UNEXPECTED_ERROR -> {
        status = "error";
        reasonCode = "unexpected_error";
        message = "Unexpected error during verification";
        httpStatus = 500;
        level = Level.SEVERE;
      }
      case MATCH, STRICT_MISMATCH -> {
        status = "error";
        reasonCode = "unexpected_state";
        message = "Unexpected verification state: " + reason;
        httpStatus = 500;
        level = Level.SEVERE;
      }
      default -> throw new IllegalStateException("Unhandled verification reason " + reason);
    }

    TelemetryFrame frame =
        TelemetryContracts.ocraVerificationAdapter()
            .status(status, telemetryId, reasonCode, true, message, payload.fields());

    logVerification(level, httpStatus, auditContext, frame);

    throw new OcraVerificationValidationException(
        telemetryId,
        payload.suite(),
        null,
        reasonCodeFor(reason),
        "Verification request invalid",
        true,
        null);
  }

  private static VerificationLogPayload verificationPayloadForResult(
      VerificationResult result, CommandEnvelope envelope, String outcome, long durationMillis) {
    VerificationContext context = envelope.normalized().context();
    String suite = result.suite();
    String fingerprint = suite == null ? null : fingerprint(suite, context);
    String otpHash = hashOtp(envelope.otp());
    Map<String, Object> fields =
        verificationFields(
            suite,
            envelope.credentialSource(),
            normalizedMode(envelope.credentialSource()),
            result.credentialId(),
            otpHash,
            fingerprint,
            outcome,
            durationMillis);
    return new VerificationLogPayload(Objects.requireNonNullElse(suite, "unknown"), fields);
  }

  private static VerificationLogPayload verificationPayloadForFailure(
      CommandEnvelope envelope, OcraVerificationRequest raw, String outcome, long durationMillis) {
    String suite =
        Optional.ofNullable(envelope)
            .map(CommandEnvelope::normalized)
            .filter(n -> n instanceof NormalizedRequest.Inline)
            .map(n -> ((NormalizedRequest.Inline) n).suite())
            .orElseGet(() -> suiteFrom(raw).orElse(null));

    String credentialSource =
        envelope != null ? envelope.credentialSource() : credentialSource(raw);
    String credentialId =
        envelope != null && envelope.command() instanceof VerificationCommand.Stored stored
            ? stored.credentialId()
            : raw.credentialId();
    String otpHash = hashOtp(envelope != null ? envelope.otp() : raw.otp());
    VerificationContext context =
        envelope != null
            ? envelope.normalized().context()
            : fallbackContext(raw, suite, credentialSource);
    String fingerprint = suite == null ? null : fingerprint(suite, context);

    Map<String, Object> fields =
        verificationFields(
            suite,
            credentialSource,
            normalizedMode(credentialSource),
            credentialId,
            otpHash,
            fingerprint,
            outcome,
            durationMillis);
    return new VerificationLogPayload(Objects.requireNonNullElse(suite, "unknown"), fields);
  }

  private static Map<String, Object> verificationFields(
      String suite,
      String credentialSource,
      String mode,
      String credentialId,
      String otpHash,
      String contextFingerprint,
      String outcome,
      long durationMillis) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("suite", Objects.requireNonNullElse(suite, "unknown"));
    fields.put("credentialSource", Objects.requireNonNullElse(credentialSource, "unknown"));
    fields.put("mode", Objects.requireNonNullElse(mode, "unknown"));
    fields.put("credentialId", Objects.requireNonNullElse(credentialId, "unknown"));
    fields.put("otpHash", Objects.requireNonNullElse(otpHash, "unavailable"));
    fields.put("contextFingerprint", Objects.requireNonNullElse(contextFingerprint, "unavailable"));
    fields.put("outcome", outcome);
    fields.put("durationMillis", durationMillis);
    return fields;
  }

  private static void logVerification(
      Level level, int httpStatus, OcraVerificationAuditContext context, TelemetryFrame frame) {
    StringBuilder builder =
        new StringBuilder("event=rest.")
            .append(frame.event())
            .append(" status=")
            .append(frame.status());

    frame
        .fields()
        .forEach((key, value) -> builder.append(' ').append(key).append('=').append(value));

    builder
        .append(' ')
        .append("httpStatus=")
        .append(httpStatus)
        .append(' ')
        .append("requestId=")
        .append(Objects.requireNonNullElse(context.requestId(), "rest-ocra-request-unknown"))
        .append(' ')
        .append("clientId=")
        .append(Objects.requireNonNullElse(context.clientId(), "unspecified"))
        .append(' ')
        .append("operator=")
        .append(context.resolvedOperatorPrincipal());

    LogRecord record = new LogRecord(level, builder.toString());
    TELEMETRY_LOGGER.log(record);
    for (Handler handler : TELEMETRY_LOGGER.getHandlers()) {
      handler.publish(record);
      handler.flush();
    }
  }

  private static VerificationContext fallbackContext(
      OcraVerificationRequest request, String suite, String credentialSource) {
    OcraVerificationContext payload = request.context();
    return new VerificationContext(
        credentialSource,
        request.credentialId(),
        suite,
        payload != null ? trim(payload.challenge()) : null,
        payload != null ? trim(payload.clientChallenge()) : null,
        payload != null ? trim(payload.serverChallenge()) : null,
        payload != null ? trim(payload.sessionHex()) : null,
        payload != null ? trim(payload.pinHashHex()) : null,
        payload != null ? trim(payload.timestampHex()) : null,
        payload != null ? payload.counter() : null);
  }

  private static String credentialSource(OcraVerificationRequest request) {
    if (request.credentialId() != null && !request.credentialId().isBlank()) {
      return "stored";
    }
    if (request.inlineCredential() != null) {
      return "inline";
    }
    return "unknown";
  }

  private static String normalizedMode(String credentialSource) {
    if (credentialSource == null || credentialSource.isBlank()) {
      return "unknown";
    }
    return credentialSource;
  }

  private static Optional<String> suiteFrom(OcraVerificationRequest request) {
    if (request.inlineCredential() == null) {
      return Optional.empty();
    }
    String value = request.inlineCredential().suite();
    if (value == null) {
      return Optional.empty();
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
  }

  private static String reasonCodeFor(VerificationReason reason) {
    return switch (reason) {
      case MATCH -> "match";
      case STRICT_MISMATCH -> "strict_mismatch";
      case VALIDATION_FAILURE -> "validation_error";
      case CREDENTIAL_NOT_FOUND -> "credential_not_found";
      case UNEXPECTED_ERROR -> "unexpected_error";
    };
  }

  private record VerificationLogPayload(String suite, Map<String, Object> fields) {
    // Marker payload object used for telemetry logging.
  }

  private static String hashOtp(String otp) {
    if (otp == null) {
      return null;
    }
    String upper = otp.trim().toUpperCase(Locale.ROOT);
    if (upper.isEmpty()) {
      return null;
    }
    return encodeSha256(upper.getBytes(StandardCharsets.UTF_8));
  }

  private static String fingerprint(String suite, VerificationContext context) {
    String joined =
        String.join(
            "|",
            Objects.requireNonNullElse(suite, "unknown"),
            Objects.requireNonNullElse(context.challenge(), ""),
            Objects.requireNonNullElse(context.clientChallenge(), ""),
            Objects.requireNonNullElse(context.serverChallenge(), ""),
            Objects.requireNonNullElse(context.sessionHex(), ""),
            Objects.requireNonNullElse(context.timestampHex(), ""),
            context.counter() == null ? "" : context.counter().toString());
    return encodeSha256(joined.getBytes(StandardCharsets.UTF_8));
  }

  private static String encodeSha256(byte[] input) {
    try {
      return BASE64_URL.encodeToString(
          java.security.MessageDigest.getInstance("SHA-256").digest(input));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  private static long toMillis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private String nextTelemetryId() {
    return "rest-ocra-" + UUID.randomUUID();
  }

  private static String trim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String inlineIdentifier(OcraVerificationInlineCredential inline) {
    return OcraInlineIdentifiers.sharedIdentifier(inline.suite(), inline.sharedSecretHex());
  }

  private record CommandEnvelope(
      VerificationCommand command,
      NormalizedRequest normalized,
      String credentialSource,
      String otp) {

    static CommandEnvelope from(OcraVerificationRequest request) {
      if (request.otp() == null || request.otp().isBlank()) {
        throw new ValidationError("otp", "otp_missing", "otp is required for verification");
      }
      boolean hasCredential = request.credentialId() != null && !request.credentialId().isBlank();
      boolean hasInline = request.inlineCredential() != null;

      if (hasCredential && hasInline) {
        throw new ValidationError(
            "request", "credential_conflict", "Provide either stored or inline credential payload");
      }
      if (!hasCredential && !hasInline) {
        throw new ValidationError(
            "request",
            "credential_missing",
            "storedCredential or inlineCredential payload must be provided");
      }

      if (hasCredential) {
        VerificationContext ctx = fallbackContext(request, null, "stored");
        VerificationCommand command =
            OcraVerificationRequests.stored(
                new OcraVerificationRequests.StoredInputs(
                    request.credentialId(),
                    request.otp(),
                    ctx.challenge(),
                    ctx.clientChallenge(),
                    ctx.serverChallenge(),
                    ctx.sessionHex(),
                    ctx.pinHashHex(),
                    ctx.timestampHex(),
                    ctx.counter()));
        NormalizedRequest normalized =
            OcraVerificationApplicationService.NormalizedRequest.from(command);
        return new CommandEnvelope(command, normalized, "stored", request.otp());
      }

      OcraVerificationInlineCredential inline = request.inlineCredential();
      if (inline.suite() == null || inline.suite().isBlank()) {
        throw new ValidationError("suite", "suite_missing", "suite is required for inline mode");
      }
      if (inline.sharedSecretHex() == null || inline.sharedSecretHex().isBlank()) {
        throw new ValidationError(
            "sharedSecretHex",
            "shared_secret_missing",
            "sharedSecretHex is required for inline mode");
      }
      VerificationContext ctx = fallbackContext(request, inline.suite(), "inline");
      VerificationCommand command =
          OcraVerificationRequests.inline(
              new OcraVerificationRequests.InlineInputs(
                  inlineIdentifier(inline),
                  inline.suite(),
                  inline.sharedSecretHex(),
                  request.otp(),
                  ctx.challenge(),
                  ctx.clientChallenge(),
                  ctx.serverChallenge(),
                  ctx.sessionHex(),
                  ctx.pinHashHex(),
                  ctx.timestampHex(),
                  ctx.counter(),
                  null));
      NormalizedRequest normalized =
          OcraVerificationApplicationService.NormalizedRequest.from(command);
      return new CommandEnvelope(command, normalized, "inline", request.otp());
    }
  }

  static final class ValidationError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String field;
    private final String reasonCode;

    ValidationError(String field, String reasonCode, String message) {
      super(message);
      this.field = field;
      this.reasonCode = reasonCode;
    }

    String field() {
      return field;
    }

    String reasonCode() {
      return reasonCode;
    }
  }
}
