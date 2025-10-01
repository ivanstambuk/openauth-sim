package io.openauth.sim.rest.ocra;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.openauth.sim.core.credentials.ocra.OcraChallengeFormat;
import io.openauth.sim.core.credentials.ocra.OcraChallengeQuestion;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraTimestampSpecification;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
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
  private final CredentialStore credentialStore;
  private final OcraCredentialPersistenceAdapter persistenceAdapter;

  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  OcraEvaluationService(
      OcraEvaluationTelemetry telemetry,
      ObjectProvider<Clock> clockProvider,
      ObjectProvider<CredentialStore> credentialStoreProvider) {
    OcraEvaluationTelemetry resolvedTelemetry = Objects.requireNonNull(telemetry, "telemetry");
    OcraCredentialFactory resolvedFactory = new OcraCredentialFactory();
    Clock resolvedClock = clockProvider.getIfAvailable();
    if (resolvedClock == null) {
      resolvedClock = Clock.systemUTC();
    }
    CredentialStore resolvedStore = credentialStoreProvider.getIfAvailable();
    this.telemetry = resolvedTelemetry;
    this.credentialFactory = resolvedFactory;
    this.clock = resolvedClock;
    this.credentialStore = resolvedStore;
    this.persistenceAdapter = new OcraCredentialPersistenceAdapter();
  }

  OcraEvaluationResponse evaluate(OcraEvaluationRequest rawRequest) {
    Objects.requireNonNull(rawRequest, "request");
    long started = System.nanoTime();
    String telemetryId = nextTelemetryId();

    NormalizedRequest request = null;
    try {
      request = NormalizedRequest.from(rawRequest);
      validateHexInputs(request);

      OcraCredentialDescriptor descriptor;
      boolean credentialReference;
      if (request instanceof NormalizedRequest.StoredCredential stored) {
        descriptor = resolveDescriptorFromStore(stored);
        credentialReference = true;
      } else if (request instanceof NormalizedRequest.InlineSecret inline) {
        descriptor = createDescriptorFromInline(inline);
        credentialReference = false;
      } else {
        throw new IllegalStateException("Unsupported request variant: " + request);
      }
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
          credentialReference,
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
          hasCredentialReference(request, rawRequest),
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
          hasCredentialReference(request, rawRequest),
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

  private OcraCredentialDescriptor createDescriptorFromInline(
      NormalizedRequest.InlineSecret request) {
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

  private OcraCredentialDescriptor resolveDescriptorFromStore(
      NormalizedRequest.StoredCredential request) {
    if (credentialStore == null) {
      throw new ValidationError(
          "credentialId",
          "credential_not_found",
          "credentialId " + request.credentialId() + " not found");
    }
    return credentialStore
        .findByName(request.credentialId())
        .map(VersionedCredentialRecordMapper::toRecord)
        .map(persistenceAdapter::deserialize)
        .orElseThrow(
            () ->
                new ValidationError(
                    "credentialId",
                    "credential_not_found",
                    "credentialId " + request.credentialId() + " not found"));
  }

  private String nextTelemetryId() {
    return "rest-ocra-" + UUID.randomUUID();
  }

  static Instant resolveTimestamp(OcraCredentialDescriptor descriptor, String timestampHex) {
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

  static void validateChallenge(OcraCredentialDescriptor descriptor, String challenge) {
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

  static boolean matchesFormat(String value, OcraChallengeFormat format) {
    return switch (format) {
      case NUMERIC -> value.chars().allMatch(Character::isDigit);
      case ALPHANUMERIC -> value.chars().allMatch(ch -> Character.isLetterOrDigit((char) ch));
      case HEX -> value.chars().allMatch(OcraEvaluationService::isHexCharacter);
      case CHARACTER -> true;
    };
  }

  static boolean isHexCharacter(int ch) {
    char value = Character.toUpperCase((char) ch);
    return (value >= '0' && value <= '9') || (value >= 'A' && value <= 'F');
  }

  private static void validateHexInputs(NormalizedRequest request) {
    if (request instanceof NormalizedRequest.InlineSecret inline) {
      requireHex(inline.sharedSecretHex(), "sharedSecretHex", true);
    }
    requireHex(request.sessionHex(), "sessionHex", false);
    requireHex(request.pinHashHex(), "pinHashHex", false);
    requireHex(request.timestampHex(), "timestampHex", false);
  }

  static void requireHex(String value, String field, boolean required) {
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

  static String suiteOrUnknown(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    if (normalized != null && hasText(normalized.suite())) {
      return normalized.suite();
    }
    String suite = raw.suite();
    return suite == null ? "unknown" : suite.trim();
  }

  static boolean hasSession(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.sessionHex()) : hasText(raw.sessionHex());
  }

  static boolean hasClientChallenge(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null
        ? hasText(normalized.clientChallenge())
        : hasText(raw.clientChallenge());
  }

  static boolean hasServerChallenge(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null
        ? hasText(normalized.serverChallenge())
        : hasText(raw.serverChallenge());
  }

  static boolean hasPin(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.pinHashHex()) : hasText(raw.pinHashHex());
  }

  static boolean hasTimestamp(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.timestampHex()) : hasText(raw.timestampHex());
  }

  static boolean hasCredentialReference(NormalizedRequest normalized, OcraEvaluationRequest raw) {
    return normalized != null ? hasText(normalized.credentialId()) : hasText(raw.credentialId());
  }

  sealed interface NormalizedRequest
      permits NormalizedRequest.StoredCredential, NormalizedRequest.InlineSecret {

    String challenge();

    String sessionHex();

    String clientChallenge();

    String serverChallenge();

    String pinHashHex();

    String timestampHex();

    Long counter();

    default String credentialId() {
      return null;
    }

    default String suite() {
      return null;
    }

    default String sharedSecretHex() {
      return null;
    }

    static NormalizedRequest from(OcraEvaluationRequest request) {
      String credentialId = normalize(request.credentialId());
      String suite = normalize(request.suite());
      String sharedSecretHex = normalize(request.sharedSecretHex());
      String challenge = normalize(request.challenge());
      String sessionHex = normalize(request.sessionHex());
      String clientChallenge = normalize(request.clientChallenge());
      String serverChallenge = normalize(request.serverChallenge());
      String pinHashHex = normalize(request.pinHashHex());
      String timestampHex = normalize(request.timestampHex());
      Long counter = request.counter();

      boolean hasCredential = hasText(credentialId);
      boolean hasSecret = hasText(sharedSecretHex);

      if (hasCredential && hasSecret) {
        throw new ValidationError(
            "request",
            "credential_conflict",
            "Provide either credentialId or sharedSecretHex, not both");
      }

      if (!hasCredential && !hasSecret) {
        throw new ValidationError(
            "request", "credential_missing", "credentialId or sharedSecretHex must be provided");
      }

      if (hasCredential) {
        return new StoredCredential(
            credentialId,
            challenge,
            sessionHex,
            clientChallenge,
            serverChallenge,
            pinHashHex,
            timestampHex,
            counter);
      }

      if (!hasText(suite)) {
        throw new ValidationError("suite", "missing_required", "suite is required");
      }

      return new InlineSecret(
          suite,
          sharedSecretHex,
          challenge,
          sessionHex,
          clientChallenge,
          serverChallenge,
          pinHashHex,
          timestampHex,
          counter);
    }

    private static String normalize(String value) {
      if (value == null) {
        return null;
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }

    record StoredCredential(
        String credentialId,
        String challenge,
        String sessionHex,
        String clientChallenge,
        String serverChallenge,
        String pinHashHex,
        String timestampHex,
        Long counter)
        implements NormalizedRequest {

      @Override
      public String credentialId() {
        return credentialId;
      }
    }

    record InlineSecret(
        String suite,
        String sharedSecretHex,
        String challenge,
        String sessionHex,
        String clientChallenge,
        String serverChallenge,
        String pinHashHex,
        String timestampHex,
        Long counter)
        implements NormalizedRequest {

      @Override
      public String suite() {
        return suite;
      }

      @Override
      public String sharedSecretHex() {
        return sharedSecretHex;
      }
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

  static record FailureDetails(String field, String reasonCode, String message, boolean sanitized) {

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
