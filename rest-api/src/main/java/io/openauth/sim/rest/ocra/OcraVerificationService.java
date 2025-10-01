package io.openauth.sim.rest.ocra;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraInlineVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraStoredVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationReason;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationResult;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationStatus;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
class OcraVerificationService {

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private final CredentialStore credentialStore;
  private final OcraReplayVerifier storedVerifier;
  private final OcraReplayVerifier inlineVerifier;
  private final OcraCredentialFactory credentialFactory;
  private final OcraCredentialPersistenceAdapter persistenceAdapter;
  private final Clock clock;
  private final OcraVerificationTelemetry telemetry;

  @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
  OcraVerificationService(
      OcraVerificationTelemetry telemetry,
      ObjectProvider<CredentialStore> credentialStoreProvider,
      ObjectProvider<Clock> clockProvider) {
    this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    this.credentialStore = credentialStoreProvider.getIfAvailable();
    this.storedVerifier = new OcraReplayVerifier(this.credentialStore);
    this.inlineVerifier = new OcraReplayVerifier(null);
    this.credentialFactory = new OcraCredentialFactory();
    this.persistenceAdapter = new OcraCredentialPersistenceAdapter();
    Clock resolvedClock = clockProvider.getIfAvailable();
    this.clock = resolvedClock == null ? Clock.systemUTC() : resolvedClock;
  }

  OcraVerificationResponse verify(
      OcraVerificationRequest rawRequest, OcraVerificationAuditContext auditContext) {
    Objects.requireNonNull(rawRequest, "request");
    Objects.requireNonNull(auditContext, "auditContext");

    String telemetryId = nextTelemetryId();
    long started = System.nanoTime();

    NormalizedRequest request = null;
    try {
      request = NormalizedRequest.from(rawRequest);
      io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext
          verificationContext = request.context().toCoreContext();

      if (request instanceof NormalizedRequest.StoredCredential stored) {
        return verifyStored(stored, auditContext, telemetryId, started, verificationContext);
      }
      if (request instanceof NormalizedRequest.InlineSecret inline) {
        return verifyInline(inline, auditContext, telemetryId, started, verificationContext);
      }
      throw new IllegalStateException("Unsupported request variant: " + request);
    } catch (ValidationProblem problem) {
      OcraVerificationTelemetry.TelemetryFrame frame =
          telemetryFrameForFailure(
              telemetryId,
              request != null ? request.credentialSource() : "unknown",
              request != null ? request.credentialId() : null,
              request != null ? request.suite().orElse(null) : suiteFrom(rawRequest).orElse(null),
              request != null ? request.otp() : rawRequest.otp(),
              request != null ? request.context() : VerificationContext.empty(),
              toMillis(started));

      telemetry.recordValidationFailure(
          auditContext,
          frame.withReasonCode(problem.reasonCode()).withOutcome("invalid"),
          problem.getMessage());

      throw validationException(
          telemetryId,
          frame.normalizedSuite(),
          problem.field(),
          problem.reasonCode(),
          problem.getMessage(),
          problem.sanitized(),
          problem);
    } catch (IllegalArgumentException ex) {
      OcraVerificationTelemetry.TelemetryFrame frame =
          telemetryFrameForFailure(
              telemetryId,
              request != null ? request.credentialSource() : "unknown",
              request != null ? request.credentialId() : null,
              request != null ? request.suite().orElse(null) : suiteFrom(rawRequest).orElse(null),
              request != null ? request.otp() : rawRequest.otp(),
              request != null ? request.context() : VerificationContext.empty(),
              toMillis(started));

      telemetry.recordValidationFailure(
          auditContext,
          frame.withReasonCode("validation_failure").withOutcome("invalid"),
          ex.getMessage());

      throw validationException(
          telemetryId,
          frame.normalizedSuite(),
          null,
          "validation_failure",
          ex.getMessage(),
          true,
          ex);
    }
  }

  private OcraVerificationResponse verifyStored(
      NormalizedRequest.StoredCredential request,
      OcraVerificationAuditContext auditContext,
      String telemetryId,
      long started,
      io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext
          verificationContext) {

    if (credentialStore == null) {
      emitCredentialNotFoundTelemetry(
          auditContext, telemetryId, request, "Credential store not configured", toMillis(started));
      throw validationException(
          telemetryId,
          request.suite().orElse(null),
          "credentialId",
          "credential_not_found",
          "credential store not configured",
          true,
          null);
    }

    String credentialId = request.credentialId();
    VersionedCredentialRecord record =
        credentialStore
            .findByName(credentialId)
            .map(VersionedCredentialRecordMapper::toRecord)
            .orElse(null);

    if (record == null) {
      emitCredentialNotFoundTelemetry(
          auditContext,
          telemetryId,
          request,
          "credentialId %s not found".formatted(credentialId),
          toMillis(started));
      throw validationException(
          telemetryId,
          request.suite().orElse(null),
          "credentialId",
          "credential_not_found",
          "credentialId %s not found".formatted(credentialId),
          true,
          null);
    }

    OcraCredentialDescriptor descriptor = persistenceAdapter.deserialize(record);
    OcraVerificationResult result =
        storedVerifier.verifyStored(
            new OcraStoredVerificationRequest(
                descriptor.name(), request.otp(), verificationContext));

    return emitResponse(
        request,
        descriptor,
        auditContext,
        telemetryId,
        started,
        result,
        "stored",
        descriptor.name());
  }

  private OcraVerificationResponse verifyInline(
      NormalizedRequest.InlineSecret request,
      OcraVerificationAuditContext auditContext,
      String telemetryId,
      long started,
      io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext
          verificationContext) {

    InlineSecretPayload inlineSecret =
        request
            .inlineSecret()
            .orElseThrow(
                () ->
                    new ValidationProblem(
                        "inlineCredential",
                        "inline_secret_missing",
                        "inlineCredential.sharedSecretHex is required",
                        request.suite().orElse(null),
                        true));

    OcraCredentialDescriptor descriptor = createDescriptor(inlineSecret);
    OcraInlineVerificationRequest inlineRequest =
        new OcraInlineVerificationRequest(
            descriptor.name(),
            descriptor.suite().value(),
            inlineSecret.sharedSecretHex(),
            SecretEncoding.HEX,
            request.otp(),
            verificationContext,
            descriptor.metadata());

    OcraVerificationResult result = inlineVerifier.verifyInline(inlineRequest);

    return emitResponse(
        request,
        descriptor,
        auditContext,
        telemetryId,
        started,
        result,
        "inline",
        descriptor.name());
  }

  private OcraVerificationResponse emitResponse(
      NormalizedRequest request,
      OcraCredentialDescriptor descriptor,
      OcraVerificationAuditContext auditContext,
      String telemetryId,
      long started,
      OcraVerificationResult result,
      String credentialSource,
      String credentialId) {

    String suite = descriptor.suite().value();
    long durationMillis = toMillis(started);
    String otpHash = hashOtp(request.otp());
    String fingerprint = fingerprint(suite, request.context());
    String reasonCode = reasonCodeFor(result.reason());
    String outcome = outcomeFor(result.status());

    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            telemetryId,
            suite,
            credentialSource,
            credentialId,
            otpHash,
            fingerprint,
            reasonCode,
            outcome,
            durationMillis,
            true);

    OcraVerificationMetadata metadata =
        new OcraVerificationMetadata(
            credentialSource,
            suite,
            descriptor.suite().cryptoFunction().responseDigits(),
            durationMillis,
            fingerprint,
            telemetryId,
            outcome);

    OcraVerificationResponse response =
        new OcraVerificationResponse(statusFor(result.status()), reasonCode, metadata);

    switch (result.status()) {
      case MATCH -> telemetry.recordMatch(auditContext, frame);
      case MISMATCH -> telemetry.recordMismatch(auditContext, frame);
      case INVALID ->
          handleInvalid(
              request,
              descriptor,
              credentialSource,
              credentialId,
              result.reason(),
              telemetryId,
              auditContext,
              durationMillis);
    }

    return response;
  }

  void handleInvalid(
      NormalizedRequest request,
      OcraCredentialDescriptor descriptor,
      String credentialSource,
      String credentialId,
      OcraVerificationReason reason,
      String telemetryId,
      OcraVerificationAuditContext auditContext,
      long durationMillis) {

    String suite = descriptor == null ? request.suite().orElse(null) : descriptor.suite().value();
    String otpHash = hashOtp(request.otp());
    String fingerprint = suite == null ? null : fingerprint(suite, request.context());

    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            telemetryId,
            suite,
            credentialSource,
            credentialId,
            otpHash,
            fingerprint,
            reasonCodeFor(reason),
            "invalid",
            durationMillis,
            true);

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
                  .formatted(Objects.requireNonNullElse(request.credentialId(), "unknown")));
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

    switch (reason) {
      case VALIDATION_FAILURE ->
          throw validationException(
              telemetryId,
              suite,
              null,
              "validation_failure",
              "Verification inputs failed validation",
              true,
              null);
      case CREDENTIAL_NOT_FOUND ->
          throw validationException(
              telemetryId,
              suite,
              "credentialId",
              "credential_not_found",
              "credentialId %s not found"
                  .formatted(Objects.requireNonNullElse(request.credentialId(), "unknown")),
              true,
              null);
      case UNEXPECTED_ERROR ->
          throw new IllegalStateException("Unexpected error during OCRA verification");
      case MATCH, STRICT_MISMATCH ->
          throw new IllegalStateException("Unexpected verification state: " + reason);
    }
  }

  void emitCredentialNotFoundTelemetry(
      OcraVerificationAuditContext auditContext,
      String telemetryId,
      NormalizedRequest request,
      String reason,
      long durationMillis) {
    OcraVerificationTelemetry.TelemetryFrame frame =
        telemetryFrameForFailure(
            telemetryId,
            request != null ? request.credentialSource() : "stored",
            request != null ? request.credentialId() : null,
            request != null ? request.suite().orElse(null) : null,
            request != null ? request.otp() : null,
            request != null ? request.context() : VerificationContext.empty(),
            durationMillis);
    telemetry.recordCredentialNotFound(
        auditContext, frame.withReasonCode("credential_not_found").withOutcome("invalid"), reason);
  }

  private OcraCredentialDescriptor createDescriptor(InlineSecretPayload inlineSecret) {
    OcraCredentialRequest credentialRequest =
        new OcraCredentialRequest(
            inlineSecret.descriptorName(),
            inlineSecret.suite(),
            inlineSecret.sharedSecretHex(),
            SecretEncoding.HEX,
            inlineSecret.counter().orElse(null),
            inlineSecret.pinHashHex(),
            null,
            Map.of("source", "rest-inline"));
    return credentialFactory.createDescriptor(credentialRequest);
  }

  private String nextTelemetryId() {
    return "rest-ocra-verify-" + UUID.randomUUID();
  }

  private static String statusFor(OcraVerificationStatus status) {
    return switch (status) {
      case MATCH -> "match";
      case MISMATCH -> "mismatch";
      case INVALID -> "invalid";
    };
  }

  private static String outcomeFor(OcraVerificationStatus status) {
    return switch (status) {
      case MATCH -> "match";
      case MISMATCH -> "mismatch";
      case INVALID -> "invalid";
    };
  }

  private static String reasonCodeFor(OcraVerificationReason reason) {
    return switch (reason) {
      case MATCH -> "match";
      case STRICT_MISMATCH -> "strict_mismatch";
      case VALIDATION_FAILURE -> "validation_failure";
      case CREDENTIAL_NOT_FOUND -> "credential_not_found";
      case UNEXPECTED_ERROR -> "unexpected_error";
    };
  }

  private static long toMillis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private OcraVerificationValidationException validationException(
      String telemetryId,
      String suite,
      String field,
      String reasonCode,
      String message,
      boolean sanitized,
      Throwable cause) {
    return new OcraVerificationValidationException(
        telemetryId, suite, field, reasonCode, message, sanitized, cause);
  }

  private OcraVerificationTelemetry.TelemetryFrame telemetryFrameForFailure(
      String telemetryId,
      String credentialSource,
      String credentialId,
      String suite,
      String otp,
      VerificationContext context,
      long durationMillis) {
    return new OcraVerificationTelemetry.TelemetryFrame(
        telemetryId,
        suite,
        credentialSource,
        credentialId,
        hashOtp(otp),
        suite == null ? null : fingerprint(suite, context),
        "validation_failure",
        "invalid",
        durationMillis,
        true);
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
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input);
      return BASE64_URL.encodeToString(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
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

  sealed interface NormalizedRequest
      permits NormalizedRequest.StoredCredential, NormalizedRequest.InlineSecret {

    VerificationContext context();

    String otp();

    String credentialSource();

    default String credentialId() {
      return null;
    }

    default Optional<String> suite() {
      return Optional.empty();
    }

    default Optional<InlineSecretPayload> inlineSecret() {
      return Optional.empty();
    }

    static NormalizedRequest from(OcraVerificationRequest request) {
      VerificationContext context = VerificationContext.from(request.context());
      String otp = normalize(request.otp());
      if (!hasText(otp)) {
        throw new ValidationProblem(
            "otp", "otp_missing", "otp is required", suiteFrom(request).orElse(null), true);
      }

      boolean hasCredential = hasText(request.credentialId());
      boolean hasInline = request.inlineCredential() != null;

      if (hasCredential && hasInline) {
        throw new ValidationProblem(
            "request",
            "credential_conflict",
            "Provide either credentialId or inlineCredential, not both",
            suiteFrom(request).orElse(null),
            true);
      }

      if (!hasCredential && !hasInline) {
        throw new ValidationProblem(
            "request",
            "credential_missing",
            "credentialId or inlineCredential is required",
            suiteFrom(request).orElse(null),
            true);
      }

      if (hasCredential) {
        return new StoredCredential(request.credentialId().trim(), context, otp);
      }

      InlineSecretPayload inline = InlineSecretPayload.from(request.inlineCredential(), context);
      return new InlineSecret(inline, context, otp);
    }

    private static String normalize(String value) {
      if (value == null) {
        return null;
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }

    record StoredCredential(String credentialId, VerificationContext context, String otp)
        implements NormalizedRequest {

      @Override
      public String credentialSource() {
        return "stored";
      }

      @Override
      public String credentialId() {
        return credentialId;
      }
    }

    record InlineSecret(InlineSecretPayload payload, VerificationContext context, String otp)
        implements NormalizedRequest {

      @Override
      public String credentialSource() {
        return "inline";
      }

      @Override
      public Optional<String> suite() {
        return Optional.of(payload.suite());
      }

      @Override
      public Optional<InlineSecretPayload> inlineSecret() {
        return Optional.of(payload);
      }
    }
  }

  static record VerificationContext(
      Long counter,
      String challenge,
      String clientChallenge,
      String serverChallenge,
      String sessionHex,
      String timestampHex,
      String pinHashHex) {

    static VerificationContext from(io.openauth.sim.rest.ocra.OcraVerificationContext context) {
      if (context == null) {
        throw new ValidationProblem(
            "context", "context_missing", "context is required", null, true);
      }
      return new VerificationContext(
          context.counter(),
          normalize(context.challenge()),
          normalize(context.clientChallenge()),
          normalize(context.serverChallenge()),
          normalize(context.sessionHex()),
          normalize(context.timestampHex()),
          normalize(context.pinHashHex()));
    }

    static VerificationContext empty() {
      return new VerificationContext(null, null, null, null, null, null, null);
    }

    io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext
        toCoreContext() {
      return new io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext(
          counter,
          challenge,
          sessionHex,
          clientChallenge,
          serverChallenge,
          pinHashHex,
          timestampHex);
    }
  }

  static record InlineSecretPayload(
      String suite,
      String sharedSecretHex,
      Optional<Long> counter,
      String pinHashHex,
      String descriptorName) {

    static InlineSecretPayload from(
        OcraVerificationInlineCredential inlineCredential, VerificationContext context) {
      if (inlineCredential == null) {
        throw new ValidationProblem(
            "inlineCredential",
            "inline_secret_missing",
            "inlineCredential.sharedSecretHex is required",
            null,
            true);
      }

      String suite = normalize(inlineCredential.suite());
      if (!hasText(suite)) {
        throw new ValidationProblem(
            "inlineCredential.suite",
            "suite_missing",
            "suite is required for inline credentials",
            null,
            true);
      }

      String secret = normalizeHex(inlineCredential.sharedSecretHex());
      if (!hasText(secret)) {
        throw new ValidationProblem(
            "inlineCredential.sharedSecretHex",
            "secret_missing",
            "sharedSecretHex is required",
            suite,
            true);
      }

      String descriptorName =
          "rest-inline-"
              + Integer.toHexString(Objects.hash(suite.toUpperCase(Locale.ROOT), secret));

      return new InlineSecretPayload(
          suite,
          secret,
          Optional.ofNullable(context.counter()),
          context.pinHashHex(),
          descriptorName);
    }
  }

  private static final class ValidationProblem extends RuntimeException {
    private final String field;
    private final String reasonCode;
    private final String suite;
    private final boolean sanitized;

    ValidationProblem(
        String field, String reasonCode, String message, String suite, boolean sanitized) {
      super(message);
      this.field = field;
      this.reasonCode = reasonCode;
      this.suite = suite;
      this.sanitized = sanitized;
    }

    String field() {
      return field;
    }

    String reasonCode() {
      return reasonCode;
    }

    String suite() {
      return suite;
    }

    boolean sanitized() {
      return sanitized;
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String normalizeHex(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.replace(" ", "").trim();
    return stripped.isEmpty() ? null : stripped;
  }
}
