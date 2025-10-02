package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationValidationException;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.NormalizedRequest;
import io.openauth.sim.application.ocra.OcraEvaluationRequests;
import io.openauth.sim.application.ocra.OcraInlineIdentifiers;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OcraEvaluationService {

  private final OcraEvaluationApplicationService applicationService;
  private final OcraEvaluationTelemetry telemetry;

  OcraEvaluationService(
      OcraEvaluationApplicationService applicationService, OcraEvaluationTelemetry telemetry) {
    this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
  }

  OcraEvaluationResponse evaluate(OcraEvaluationRequest rawRequest) {
    Objects.requireNonNull(rawRequest, "request");

    long started = System.nanoTime();
    String telemetryId = nextTelemetryId();
    CommandEnvelope envelope = null;

    try {
      envelope = CommandEnvelope.from(rawRequest);
      EvaluationResult result = applicationService.evaluate(envelope.command());
      NormalizedRequest normalized = result.request();
      long durationMillis = toMillis(started);

      telemetry.recordSuccess(
          telemetryId,
          result.suite(),
          result.credentialReference(),
          hasText(normalized.sessionHex()),
          hasText(normalized.clientChallenge()),
          hasText(normalized.serverChallenge()),
          hasText(normalized.pinHashHex()),
          hasText(normalized.timestampHex()),
          durationMillis);

      return new OcraEvaluationResponse(result.suite(), result.otp(), telemetryId);
    } catch (EvaluationValidationException ex) {
      long durationMillis = toMillis(started);
      FailureDetails failure = FailureDetails.from(ex);
      String suite = suiteOrUnknown(envelope, rawRequest);

      telemetry.recordValidationFailure(
          telemetryId,
          suite,
          hasCredentialReference(envelope, rawRequest),
          hasSession(envelope, rawRequest),
          hasClientChallenge(envelope, rawRequest),
          hasServerChallenge(envelope, rawRequest),
          hasPin(envelope, rawRequest),
          hasTimestamp(envelope, rawRequest),
          failure.reasonCode(),
          failure.message(),
          failure.sanitized(),
          durationMillis);

      throw new OcraEvaluationValidationException(
          telemetryId,
          suite,
          failure.field(),
          failure.reasonCode(),
          failure.message(),
          failure.sanitized(),
          ex);
    } catch (ValidationError ex) {
      long durationMillis = toMillis(started);
      FailureDetails failure = FailureDetails.from(ex);
      String suite = suiteOrUnknown(envelope, rawRequest);

      telemetry.recordValidationFailure(
          telemetryId,
          suite,
          hasCredentialReference(envelope, rawRequest),
          hasSession(envelope, rawRequest),
          hasClientChallenge(envelope, rawRequest),
          hasServerChallenge(envelope, rawRequest),
          hasPin(envelope, rawRequest),
          hasTimestamp(envelope, rawRequest),
          failure.reasonCode(),
          failure.message(),
          failure.sanitized(),
          durationMillis);

      throw new OcraEvaluationValidationException(
          telemetryId, suite, failure.field(), failure.reasonCode(), failure.message(), true, ex);
    } catch (IllegalArgumentException ex) {
      long durationMillis = toMillis(started);
      FailureDetails failure = FailureDetails.fromIllegalArgument(ex.getMessage());
      String suite = suiteOrUnknown(envelope, rawRequest);

      telemetry.recordValidationFailure(
          telemetryId,
          suite,
          hasCredentialReference(envelope, rawRequest),
          hasSession(envelope, rawRequest),
          hasClientChallenge(envelope, rawRequest),
          hasServerChallenge(envelope, rawRequest),
          hasPin(envelope, rawRequest),
          hasTimestamp(envelope, rawRequest),
          failure.reasonCode(),
          failure.message(),
          true,
          durationMillis);

      throw new OcraEvaluationValidationException(
          telemetryId, suite, failure.field(), failure.reasonCode(), failure.message(), true, ex);
    } catch (RuntimeException ex) {
      long durationMillis = toMillis(started);
      String suite = suiteOrUnknown(envelope, rawRequest);

      telemetry.recordError(
          telemetryId,
          suite,
          hasCredentialReference(envelope, rawRequest),
          hasSession(envelope, rawRequest),
          hasClientChallenge(envelope, rawRequest),
          hasServerChallenge(envelope, rawRequest),
          hasPin(envelope, rawRequest),
          hasTimestamp(envelope, rawRequest),
          "unexpected_error",
          ex.getMessage(),
          false,
          durationMillis);
      throw ex;
    }
  }

  private static boolean hasSession(CommandEnvelope envelope, OcraEvaluationRequest raw) {
    return envelope != null
        ? hasText(envelope.normalized().sessionHex())
        : hasText(raw.sessionHex());
  }

  private static boolean hasClientChallenge(CommandEnvelope envelope, OcraEvaluationRequest raw) {
    return envelope != null
        ? hasText(envelope.normalized().clientChallenge())
        : hasText(raw.clientChallenge());
  }

  private static boolean hasServerChallenge(CommandEnvelope envelope, OcraEvaluationRequest raw) {
    return envelope != null
        ? hasText(envelope.normalized().serverChallenge())
        : hasText(raw.serverChallenge());
  }

  private static boolean hasPin(CommandEnvelope envelope, OcraEvaluationRequest raw) {
    return envelope != null
        ? hasText(envelope.normalized().pinHashHex())
        : hasText(raw.pinHashHex());
  }

  private static boolean hasTimestamp(CommandEnvelope envelope, OcraEvaluationRequest raw) {
    return envelope != null
        ? hasText(envelope.normalized().timestampHex())
        : hasText(raw.timestampHex());
  }

  private static boolean hasCredentialReference(
      CommandEnvelope envelope, OcraEvaluationRequest raw) {
    if (envelope != null) {
      return envelope.credentialReference();
    }
    return hasText(raw.credentialId());
  }

  private static String suiteOrUnknown(CommandEnvelope envelope, OcraEvaluationRequest raw) {
    if (envelope != null) {
      NormalizedRequest normalized = envelope.normalized();
      if (normalized instanceof NormalizedRequest.InlineSecret inline) {
        return inline.suite();
      }
      if (envelope.requestedSuite() != null) {
        return envelope.requestedSuite();
      }
    }
    String suite = raw.suite();
    return suite == null || suite.isBlank() ? "unknown" : suite.trim();
  }

  private static long toMillis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String nextTelemetryId() {
    return "rest-ocra-" + UUID.randomUUID();
  }

  private record CommandEnvelope(
      EvaluationCommand command,
      NormalizedRequest normalized,
      boolean credentialReference,
      String requestedSuite) {

    static CommandEnvelope from(OcraEvaluationRequest request) {
      boolean hasCredential = hasText(request.credentialId());
      boolean hasInline = hasText(request.sharedSecretHex());

      if (hasCredential && hasInline) {
        throw new ValidationError(
            "request",
            "credential_conflict",
            "Provide either credentialId or sharedSecretHex, not both");
      }
      if (!hasCredential && !hasInline) {
        throw new ValidationError(
            "request", "credential_missing", "credentialId or sharedSecretHex must be provided");
      }

      if (hasCredential) {
        EvaluationCommand command =
            OcraEvaluationRequests.stored(
                new OcraEvaluationRequests.StoredInputs(
                    request.credentialId(),
                    request.challenge(),
                    request.sessionHex(),
                    request.clientChallenge(),
                    request.serverChallenge(),
                    request.pinHashHex(),
                    request.timestampHex(),
                    request.counter()));
        NormalizedRequest normalized =
            OcraEvaluationApplicationService.NormalizedRequest.from(command);
        return new CommandEnvelope(command, normalized, true, request.suite());
      }

      String suite = request.suite();
      if (!hasText(suite)) {
        throw new ValidationError("suite", "missing_required", "suite is required for inline mode");
      }

      String identifier = inlineIdentifier(request);
      EvaluationCommand command =
          OcraEvaluationRequests.inline(
              new OcraEvaluationRequests.InlineInputs(
                  identifier,
                  suite,
                  request.sharedSecretHex(),
                  request.challenge(),
                  request.sessionHex(),
                  request.clientChallenge(),
                  request.serverChallenge(),
                  request.pinHashHex(),
                  request.timestampHex(),
                  request.counter(),
                  null));
      NormalizedRequest normalized =
          OcraEvaluationApplicationService.NormalizedRequest.from(command);
      return new CommandEnvelope(command, normalized, false, suite);
    }

    private static String inlineIdentifier(OcraEvaluationRequest request) {
      return OcraInlineIdentifiers.sharedIdentifier(request.suite(), request.sharedSecretHex());
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

  record FailureDetails(String field, String reasonCode, String message, boolean sanitized) {

    static FailureDetails from(EvaluationValidationException exception) {
      return new FailureDetails(
          exception.field(), exception.reasonCode(), exception.getMessage(), exception.sanitized());
    }

    static FailureDetails from(ValidationError error) {
      return new FailureDetails(error.field(), error.reasonCode(), error.getMessage(), true);
    }

    static FailureDetails fromIllegalArgument(String message) {
      if (message == null || message.isBlank()) {
        return new FailureDetails("request", "invalid_input", "Invalid input", true);
      }
      String trimmed = message.trim();
      String lower = trimmed.toLowerCase(Locale.ROOT);
      if (lower.contains("pin") && lower.contains("sha1") && lower.contains("40")) {
        return new FailureDetails("pinHashHex", "pin_hash_mismatch", trimmed, true);
      }
      if (lower.contains("session") && lower.contains("required")) {
        return new FailureDetails(
            "sessionHex",
            "session_required",
            "sessionHex is required for the requested suite",
            true);
      }
      if (lower.contains("session") && lower.contains("not permitted")) {
        return new FailureDetails("sessionHex", "session_not_permitted", trimmed, true);
      }
      if (lower.contains("timestamp") && lower.contains("outside")) {
        return new FailureDetails("timestampHex", "timestamp_drift_exceeded", trimmed, true);
      }
      if (lower.contains("timestamp") && lower.contains("not permitted")) {
        return new FailureDetails("timestampHex", "timestamp_not_permitted", trimmed, true);
      }
      if (lower.contains("timestamp") && lower.contains("valid time")) {
        return new FailureDetails("timestampHex", "timestamp_invalid", trimmed, true);
      }
      if (lower.contains("timestamphex") && lower.contains("hexadecimal")) {
        return new FailureDetails("timestampHex", "timestamp_invalid", trimmed, true);
      }
      if (lower.contains("pin") && lower.contains("not permitted")) {
        return new FailureDetails("pinHashHex", "pin_hash_not_permitted", trimmed, true);
      }
      if (lower.contains("pin") && lower.contains("required")) {
        return new FailureDetails("pinHashHex", "pin_hash_required", trimmed, true);
      }
      if (lower.contains("counter") && lower.contains("required")) {
        return new FailureDetails("counter", "counter_required", trimmed, true);
      }
      if (lower.contains("counter") && lower.contains("not permitted")) {
        return new FailureDetails("counter", "counter_not_permitted", trimmed, true);
      }
      if (lower.contains("counter") && lower.contains("negative")) {
        return new FailureDetails(
            "counter", "counter_negative", "counter must be greater than or equal to zero", true);
      }
      if (lower.contains("countervalue") && lower.contains(">=")) {
        return new FailureDetails(
            "counter", "counter_negative", "counter must be greater than or equal to zero", true);
      }
      if (lower.contains("credential") && lower.contains("required")) {
        return new FailureDetails("credentialId", "credential_missing", trimmed, true);
      }
      if (lower.contains("credentialid")
          && lower.contains("sharedsecrethex")
          && lower.contains("provided")) {
        return new FailureDetails("credentialId", "credential_missing", trimmed, true);
      }
      if (lower.contains("sharedsecret") && lower.contains("required")) {
        return new FailureDetails("sharedSecretHex", "missing_required", trimmed, true);
      }
      return new FailureDetails("request", "invalid_input", trimmed, true);
    }
  }
}
