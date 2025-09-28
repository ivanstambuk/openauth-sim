package io.openauth.sim.rest.ocra;

import io.openauth.sim.core.credentials.ocra.OcraChallengeFormat;
import io.openauth.sim.core.credentials.ocra.OcraChallengeQuestion;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.SecretEncoding;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OcraEvaluationService {

  private final OcraCredentialFactory credentialFactory;
  private final OcraEvaluationTelemetry telemetry;

  OcraEvaluationService(OcraEvaluationTelemetry telemetry) {
    this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    this.credentialFactory = new OcraCredentialFactory();
  }

  OcraEvaluationResponse evaluate(OcraEvaluationRequest rawRequest) {
    Objects.requireNonNull(rawRequest, "request");
    long started = System.nanoTime();
    String telemetryId = nextTelemetryId();

    NormalizedRequest request = null;
    try {
      request = NormalizedRequest.from(rawRequest);
      OcraCredentialDescriptor descriptor = createDescriptor(request);
      String challenge = request.challenge();
      validateChallenge(descriptor, challenge);
      credentialFactory.validateSessionInformation(descriptor, request.sessionHex());

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
      telemetry.recordValidationFailure(
          telemetryId,
          suite,
          hasSession(request, rawRequest),
          hasClientChallenge(request, rawRequest),
          hasServerChallenge(request, rawRequest),
          hasPin(request, rawRequest),
          hasTimestamp(request, rawRequest),
          ex.getMessage(),
          durationMillis);
      throw new OcraEvaluationValidationException(telemetryId, suite, ex.getMessage(), ex);
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
          ex.getMessage(),
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
}
