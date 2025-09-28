package io.openauth.sim.rest.ocra;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.openauth.sim.core.credentials.ocra.OcraChallengeFormat;
import io.openauth.sim.core.credentials.ocra.OcraChallengeQuestion;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraTimestampSpecification;
import io.openauth.sim.core.model.SecretEncoding;
import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
class OcraEvaluationService {

  private final OcraCredentialFactory credentialFactory;
  private final OcraEvaluationTelemetry telemetry;
  private final Clock clock;

  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  OcraEvaluationService(OcraEvaluationTelemetry telemetry, ObjectProvider<Clock> clockProvider) {
    OcraEvaluationTelemetry resolvedTelemetry = Objects.requireNonNull(telemetry, "telemetry");
    OcraCredentialFactory resolvedFactory = new OcraCredentialFactory();
    Clock resolvedClock = clockProvider.getIfAvailable(Clock::systemUTC);
    this.telemetry = resolvedTelemetry;
    this.credentialFactory = resolvedFactory;
    this.clock = resolvedClock;
  }

  OcraEvaluationResponse evaluate(OcraEvaluationRequest rawRequest) {
    Objects.requireNonNull(rawRequest, "request");
    long started = System.nanoTime();
    String telemetryId = nextTelemetryId();

    NormalizedRequest request = null;
    try {
      request = NormalizedRequest.from(rawRequest);
      validateHexInputs(request);
      OcraCredentialDescriptor descriptor = createDescriptor(request);
      String challenge = request.challenge();
      validateChallenge(descriptor, challenge);
      credentialFactory.validateSessionInformation(descriptor, request.sessionHex());
      Instant referenceInstant = Instant.now(clock);
      Instant timestampInstant = resolveTimestamp(descriptor, request.timestampHex());
      credentialFactory.validateTimestamp(descriptor, timestampInstant, referenceInstant);

      OcraResponseCalculator.OcraExecutionContext context =
          new OcraResponseCalculator.OcraExecutionContext(
              request.counter(),
              request.challenge(),
              request.sessionHex(),
              request.clientChallenge(),
              request.serverChallenge(),
              request.pinHashHex(),
              request.timestampHex());

      String otp = OcraResponseCalculator.generate(descriptor, context);
      long durationMillis = toMillis(started);
      telemetry.recordSuccess(
          telemetryId,
          descriptor.suite().value(),
          hasText(request.sessionHex()),
          hasText(request.clientChallenge()),
          hasText(request.serverChallenge()),
          hasText(request.pinHashHex()),
          hasText(request.timestampHex()),
          durationMillis);
      return new OcraEvaluationResponse(descriptor.suite().value(), otp, telemetryId);
    } catch (IllegalArgumentException ex) {
      long durationMillis = toMillis(started);
      String suite = suiteOrUnknown(request, rawRequest);
      FailureDetails failure = FailureDetails.fromException(ex);
      telemetry.recordValidationFailure(
          telemetryId,
          suite,
          hasSession(request, rawRequest),
          hasClientChallenge(request, rawRequest),
          hasServerChallenge(request, rawRequest),
          hasPin(request, rawRequest),
          hasTimestamp(request, rawRequest),
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
    } catch (RuntimeException ex) {
      long durationMillis = toMillis(started);
      String suite = suiteOrUnknown(request, rawRequest);
      telemetry.recordError(
          telemetryId,
          suite,
          hasSession(request, rawRequest),
          hasClientChallenge(request, rawRequest),
          hasServerChallenge(request, rawRequest),
          hasPin(request, rawRequest),
          hasTimestamp(request, rawRequest),
          "unexpected_error",
          ex.getMessage(),
          false,
          durationMillis);
      throw ex;
    }
  }

  private OcraCredentialDescriptor createDescriptor(NormalizedRequest request) {
    OcraCredentialRequest credentialRequest =
        new OcraCredentialRequest(
            "rest-ocra-" + Integer.toHexString(request.suite().hashCode()),
            request.suite(),
            request.sharedSecretHex(),
            SecretEncoding.HEX,
            request.counter(),
            request.pinHashHex(),
            null,
            Map.of("source", "rest-api"));

    return credentialFactory.createDescriptor(credentialRequest);
  }

  private String nextTelemetryId() {
    return "rest-ocra-" + UUID.randomUUID();
  }

  private static Instant resolveTimestamp(
      OcraCredentialDescriptor descriptor, String timestampHex) {
    if (!hasText(timestampHex)) {
      return null;
    }

    var timestampSpec = descriptor.suite().dataInput().timestamp();
    if (timestampSpec.isEmpty()) {
      throw new ValidationError(
          "timestampHex",
          "timestamp_not_permitted",
          "timestampHex is not permitted for the requested suite");
    }

    String normalized = timestampHex.toUpperCase(Locale.ROOT);
    long timeSteps;
    try {
      timeSteps = Long.parseUnsignedLong(normalized, 16);
    } catch (NumberFormatException ex) {
      throw new ValidationError(
          "timestampHex",
          "timestamp_invalid",
          "timestampHex must be a valid hexadecimal time window");
    }

    Duration step = timestampSpec.map(OcraTimestampSpecification::step).orElse(Duration.ZERO);
    long stepSeconds = step.getSeconds();
    if (stepSeconds <= 0) {
      return null;
    }

    long epochSeconds;
    try {
      epochSeconds = Math.multiplyExact(timeSteps, stepSeconds);
    } catch (ArithmeticException ex) {
      throw new ValidationError(
          "timestampHex", "timestamp_invalid", "timestampHex exceeds supported range");
    }

    return Instant.ofEpochSecond(epochSeconds);
  }

  private static long toMillis(long started) {
    return Math.round((System.nanoTime() - started) / 1_000_000.0d);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static void validateChallenge(OcraCredentialDescriptor descriptor, String challenge) {
    var challengeSpec = descriptor.suite().dataInput().challengeQuestion();
    if (challengeSpec.isEmpty()) {
      if (hasText(challenge)) {
        throw new IllegalArgumentException(
            "challengeQuestion not permitted for suite: " + descriptor.suite().value());
      }
      return;
    }

    if (!hasText(challenge)) {
      throw new IllegalArgumentException(
          "challengeQuestion required for suite: " + descriptor.suite().value());
    }

    OcraChallengeQuestion spec = challengeSpec.orElseThrow();
    if (challenge.length() < spec.length()) {
      throw new IllegalArgumentException(
          "challengeQuestion must contain at least "
              + spec.length()
              + " characters for format "
              + spec.format());
    }

    if (!matchesFormat(challenge, spec.format())) {
      throw new IllegalArgumentException(
          "challengeQuestion must match format "
              + spec.format()
              + " for suite: "
              + descriptor.suite().value());
    }
  }

  private static boolean matchesFormat(String value, OcraChallengeFormat format) {
    return switch (format) {
      case NUMERIC -> value.chars().allMatch(Character::isDigit);
      case ALPHANUMERIC -> value.chars().allMatch(ch -> Character.isLetterOrDigit((char) ch));
      case HEX -> value.chars().allMatch(OcraEvaluationService::isHexCharacter);
      case CHARACTER -> true;
    };
  }

  private static boolean isHexCharacter(int ch) {
    char value = Character.toUpperCase((char) ch);
    return (value >= '0' && value <= '9') || (value >= 'A' && value <= 'F');
  }

  private static void validateHexInputs(NormalizedRequest request) {
    requireHex(request.sharedSecretHex(), "sharedSecretHex", true);
    requireHex(request.sessionHex(), "sessionHex", false);
    requireHex(request.pinHashHex(), "pinHashHex", false);
    requireHex(request.timestampHex(), "timestampHex", false);
  }

  private static void requireHex(String value, String field, boolean required) {
    if (!hasText(value)) {
      if (required) {
        throw new ValidationError(field, "missing_required", field + " is required");
      }
      return;
    }

    String uppercase = value.toUpperCase(Locale.ROOT);
    if (!uppercase.chars().allMatch(OcraEvaluationService::isHexCharacter)) {
      throw new ValidationError(
          field, "not_hexadecimal", field + " must contain only hexadecimal characters (0-9, A-F)");
    }

    if ((uppercase.length() & 1) == 1) {
      throw new ValidationError(
          field, "invalid_hex_length", field + " must contain an even number of characters");
    }
  }

  private static String suiteOrUnknown(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    if (normalized != null) {
      return normalized.suite();
    }
    String suite = raw.suite();
    return suite == null ? "unknown" : suite.trim();
  }

  private static boolean hasSession(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.sessionHex()) : hasText(raw.sessionHex());
  }

  private static boolean hasClientChallenge(
      NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null
        ? hasText(normalized.clientChallenge())
        : hasText(raw.clientChallenge());
  }

  private static boolean hasServerChallenge(
      NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null
        ? hasText(normalized.serverChallenge())
        : hasText(raw.serverChallenge());
  }

  private static boolean hasPin(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.pinHashHex()) : hasText(raw.pinHashHex());
  }

  private static boolean hasTimestamp(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.timestampHex()) : hasText(raw.timestampHex());
  }

  private record NormalizedRequest(
      String suite,
      String sharedSecretHex,
      String challenge,
      String sessionHex,
      String clientChallenge,
      String serverChallenge,
      String pinHashHex,
      String timestampHex,
      Long counter) {

    static NormalizedRequest from(OcraEvaluationRequest request) {
      return new NormalizedRequest(
          requireText(request.suite(), "suite"),
          requireText(request.sharedSecretHex(), "sharedSecretHex"),
          normalize(request.challenge()),
          normalize(request.sessionHex()),
          normalize(request.clientChallenge()),
          normalize(request.serverChallenge()),
          normalize(request.pinHashHex()),
          normalize(request.timestampHex()),
          request.counter());
    }

    private static String normalize(String value) {
      if (value == null) {
        return null;
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String value, String fieldName) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(fieldName + " is required");
      }
      return value.trim();
    }
  }

  private static final class ValidationError extends IllegalArgumentException {
    @Serial private static final long serialVersionUID = 1L;
    private final String field;
    private final String reasonCode;

    ValidationError(String field, String reasonCode, String message) {
      super(message);
      this.field = field;
      this.reasonCode = reasonCode;
    }
  }

  private record FailureDetails(
      String field, String reasonCode, String message, boolean sanitized) {

    static FailureDetails fromException(IllegalArgumentException ex) {
      if (ex instanceof ValidationError error) {
        return new FailureDetails(error.field, error.reasonCode, ex.getMessage(), true);
      }

      String message = messageOrDefault(ex.getMessage());
      String reasonCode = "invalid_input";
      String field = "request";

      if (message.contains("sessionInformation required")) {
        field = "sessionHex";
        reasonCode = "session_required";
        message = "sessionHex is required for the requested suite";
      } else if (message.contains("session information not permitted")
          || message.contains("sessionInformation not permitted")) {
        field = "sessionHex";
        reasonCode = "session_not_permitted";
        message = "sessionHex is not permitted for the requested suite";
      } else if (message.contains("challengeQuestion must contain")) {
        field = "challenge";
        reasonCode = "challenge_length";
        message = "challenge must match the required length";
      } else if (message.contains("challengeQuestion must match format")) {
        field = "challenge";
        reasonCode = "challenge_format";
        message = "challenge must match the suite format requirements";
      } else if (message.contains("challengeQuestion required")) {
        field = "challenge";
        reasonCode = "challenge_required";
        message = "challenge is required for the requested suite";
      } else if (message.contains("counterValue required")) {
        field = "counter";
        reasonCode = "counter_required";
        message = "counter is required for the requested suite";
      } else if (message.contains("counterValue must be >= 0")) {
        field = "counter";
        reasonCode = "counter_negative";
        message = "counter must be a non-negative number";
      } else if (message.contains("counterValue not permitted")) {
        field = "counter";
        reasonCode = "counter_not_permitted";
        message = "counter is not permitted for the requested suite";
      } else if (message.contains("sharedSecretHex is required")) {
        field = "sharedSecretHex";
        reasonCode = "missing_required";
        message = "sharedSecretHex is required";
      } else if (message.contains("timestamp outside permitted drift")) {
        field = "timestampHex";
        reasonCode = "timestamp_drift_exceeded";
        message = "timestampHex is outside the permitted drift window";
      } else if (message.contains("timestamp not permitted")) {
        field = "timestampHex";
        reasonCode = "timestamp_not_permitted";
        message = "timestampHex is not permitted for the requested suite";
      } else if (message.contains("timestamp")) {
        field = "timestampHex";
        reasonCode = "timestamp_invalid";
        message = "timestampHex must represent a valid time window";
      } else if (message.contains("pinHash must")) {
        field = "pinHashHex";
        reasonCode = "pin_hash_mismatch";
        message = "pinHashHex must match the suite hash format";
      } else if (message.contains("pinHash required")) {
        field = "pinHashHex";
        reasonCode = "pin_hash_required";
        message = "pinHashHex is required for the requested suite";
      } else if (message.contains("pinHash not permitted")) {
        field = "pinHashHex";
        reasonCode = "pin_hash_not_permitted";
        message = "pinHashHex is not permitted for the requested suite";
      }

      return new FailureDetails(field, reasonCode, message, true);
    }

    private static String messageOrDefault(String message) {
      if (message == null || message.isBlank()) {
        return "Invalid input";
      }
      return message.trim();
    }
  }
}
