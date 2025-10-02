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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OcraVerificationService {

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private final OcraVerificationApplicationService applicationService;
  private final OcraVerificationTelemetry telemetry;

  OcraVerificationService(
      OcraVerificationApplicationService applicationService, OcraVerificationTelemetry telemetry) {
    this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
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

      OcraVerificationTelemetry.TelemetryFrame frame =
          telemetryFrameForResult(result, envelope, telemetryId, durationMillis);

      switch (result.status()) {
        case MATCH -> telemetry.recordMatch(auditContext, frame.withOutcome("match"));
        case MISMATCH -> telemetry.recordMismatch(auditContext, frame.withOutcome("mismatch"));
        case INVALID -> handleInvalid(result, auditContext, frame, telemetryId, durationMillis);
      }

      if (result.status() == VerificationStatus.INVALID) {
        throw new IllegalStateException("Verification invalid state should have been handled");
      }

      OcraVerificationMetadata metadata =
          new OcraVerificationMetadata(
              envelope.credentialSource(),
              result.suite(),
              result.responseDigits(),
              durationMillis,
              frame.contextFingerprint(),
              telemetryId,
              frame.outcome());

      String status = result.status() == VerificationStatus.MATCH ? "match" : "mismatch";
      return new OcraVerificationResponse(status, reasonCodeFor(result.reason()), metadata);
    } catch (VerificationValidationException ex) {
      long durationMillis = toMillis(started);
      OcraVerificationTelemetry.TelemetryFrame frame =
          telemetryFrameForFailure(telemetryId, envelope, rawRequest, durationMillis);

      telemetry.recordValidationFailure(
          auditContext,
          frame.withReasonCode(ex.reasonCode()).withOutcome("invalid"),
          ex.getMessage());

      throw new OcraVerificationValidationException(
          telemetryId,
          frame.normalizedSuite(),
          ex.field(),
          ex.reasonCode(),
          ex.getMessage(),
          ex.sanitized(),
          ex);
    } catch (ValidationError ex) {
      long durationMillis = toMillis(started);
      OcraVerificationTelemetry.TelemetryFrame frame =
          telemetryFrameForFailure(telemetryId, envelope, rawRequest, durationMillis);

      telemetry.recordValidationFailure(
          auditContext,
          frame.withReasonCode(ex.reasonCode()).withOutcome("invalid"),
          ex.getMessage());

      throw new OcraVerificationValidationException(
          telemetryId,
          frame.normalizedSuite(),
          ex.field(),
          ex.reasonCode(),
          ex.getMessage(),
          true,
          ex);
    } catch (RuntimeException ex) {
      long durationMillis = toMillis(started);
      OcraVerificationTelemetry.TelemetryFrame frame =
          telemetryFrameForFailure(telemetryId, envelope, rawRequest, durationMillis);

      telemetry.recordUnexpectedError(
          auditContext,
          frame.withReasonCode("unexpected_error").withOutcome("error"),
          ex.getMessage());
      throw ex;
    }
  }

  private void handleInvalid(
      VerificationResult result,
      OcraVerificationAuditContext auditContext,
      OcraVerificationTelemetry.TelemetryFrame frame,
      String telemetryId,
      long durationMillis) {

    VerificationReason reason = result.reason();
    switch (reason) {
      case VALIDATION_FAILURE ->
          telemetry.recordValidationFailure(
              auditContext,
              frame.withReasonCode("validation_failure"),
              "Verification inputs failed validation");
      case CREDENTIAL_NOT_FOUND ->
          telemetry.recordCredentialNotFound(
              auditContext,
              frame.withReasonCode("credential_not_found"),
              "credentialId %s not found"
                  .formatted(Objects.requireNonNullElse(result.credentialId(), "unknown")));
      case UNEXPECTED_ERROR ->
          telemetry.recordUnexpectedError(
              auditContext,
              frame.withReasonCode("unexpected_error"),
              "Unexpected error during verification");
      case MATCH, STRICT_MISMATCH ->
          telemetry.recordUnexpectedError(
              auditContext,
              frame.withReasonCode("unexpected_state"),
              "Unexpected verification state: " + reason);
    }

    throw new OcraVerificationValidationException(
        telemetryId,
        result.suite(),
        null,
        reasonCodeFor(reason),
        "Verification request invalid",
        true,
        null);
  }

  private static OcraVerificationTelemetry.TelemetryFrame telemetryFrameForResult(
      VerificationResult result,
      CommandEnvelope envelope,
      String telemetryId,
      long durationMillis) {
    VerificationContext context = envelope.normalized().context();
    String suite = result.suite();
    String fingerprint = suite == null ? null : fingerprint(suite, context);
    String otpHash = hashOtp(envelope.otp());
    String outcome =
        switch (result.status()) {
          case MATCH -> "match";
          case MISMATCH -> "mismatch";
          case INVALID -> "invalid";
        };

    return new OcraVerificationTelemetry.TelemetryFrame(
        telemetryId,
        suite,
        envelope.credentialSource(),
        result.credentialId(),
        otpHash,
        fingerprint,
        reasonCodeFor(result.reason()),
        outcome,
        durationMillis,
        true);
  }

  private static OcraVerificationTelemetry.TelemetryFrame telemetryFrameForFailure(
      String telemetryId,
      CommandEnvelope envelope,
      OcraVerificationRequest raw,
      long durationMillis) {
    String suite =
        Optional.ofNullable(envelope)
            .map(CommandEnvelope::normalized)
            .filter(n -> n instanceof NormalizedRequest.Inline inline)
            .map(n -> ((NormalizedRequest.Inline) n).suite())
            .orElseGet(() -> suiteFrom(raw).orElse(null));

    VerificationContext context =
        envelope != null
            ? envelope.normalized().context()
            : fallbackContext(raw, suite, credentialSource(raw));
    String credentialSource =
        envelope != null ? envelope.credentialSource() : credentialSource(raw);
    String credentialId =
        envelope != null && envelope.command() instanceof VerificationCommand.Stored stored
            ? stored.credentialId()
            : raw.credentialId();
    String otpHash = hashOtp(envelope != null ? envelope.otp() : raw.otp());
    String fingerprint = suite == null ? null : fingerprint(suite, context);

    return new OcraVerificationTelemetry.TelemetryFrame(
        telemetryId,
        suite,
        credentialSource,
        credentialId,
        otpHash,
        fingerprint,
        "validation_failure",
        "invalid",
        durationMillis,
        true);
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
