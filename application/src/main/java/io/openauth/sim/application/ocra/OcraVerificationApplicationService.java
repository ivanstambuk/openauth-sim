package io.openauth.sim.application.ocra;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraInlineVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraStoredVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationResult;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class OcraVerificationApplicationService {

  private final Clock clock;
  private final CredentialResolver credentialResolver;
  private final OcraReplayVerifier storedVerifier;
  private final OcraReplayVerifier inlineVerifier;
  private final OcraCredentialFactory credentialFactory;

  public OcraVerificationApplicationService(
      Clock clock, CredentialResolver credentialResolver, CredentialStore credentialStore) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.credentialResolver = Objects.requireNonNull(credentialResolver, "credentialResolver");
    this.storedVerifier = new OcraReplayVerifier(credentialStore);
    this.inlineVerifier = new OcraReplayVerifier(null);
    this.credentialFactory = new OcraCredentialFactory();
  }

  public VerificationResult verify(VerificationCommand command) {
    Objects.requireNonNull(command, "command");
    if (!hasText(command.otp())) {
      throw new VerificationValidationException("otp", "otp_missing", "otp is required", true);
    }

    NormalizedRequest normalized = NormalizedRequest.from(command);
    OcraVerificationContext context = normalized.context().toCoreContext();

    if (normalized instanceof NormalizedRequest.Stored stored) {
      return verifyStored(stored, context);
    }
    if (normalized instanceof NormalizedRequest.Inline inline) {
      return verifyInline(inline, context);
    }
    throw new IllegalStateException("Unsupported command: " + normalized.getClass());
  }

  private VerificationResult verifyStored(
      NormalizedRequest.Stored stored, OcraVerificationContext context) {
    Optional<OcraCredentialDescriptor> descriptorOptional =
        credentialResolver.findById(stored.credentialId());
    if (descriptorOptional.isEmpty()) {
      return new VerificationResult(
          VerificationStatus.INVALID,
          VerificationReason.CREDENTIAL_NOT_FOUND,
          null,
          true,
          stored.credentialId(),
          0,
          stored);
    }

    OcraCredentialDescriptor descriptor = descriptorOptional.get();
    ensureChallengeProvided(descriptor, context);

    OcraStoredVerificationRequest request =
        new OcraStoredVerificationRequest(descriptor.name(), stored.otp(), context);

    OcraVerificationResult result = storedVerifier.verifyStored(request);
    int responseDigits = descriptor.suite().cryptoFunction().responseDigits();
    return mapResult(
        result, descriptor.suite().value(), true, descriptor.name(), responseDigits, stored);
  }

  private VerificationResult verifyInline(
      NormalizedRequest.Inline inline, OcraVerificationContext context) {
    OcraCredentialDescriptor descriptor =
        credentialFactory.createDescriptor(
            new OcraCredentialRequest(
                inline.identifier(),
                inline.suite(),
                inline.sharedSecretHex(),
                SecretEncoding.HEX,
                inline.counter(),
                inline.pinHashHex(),
                inline.allowedDrift(),
                Map.of("source", "inline")));

    ensureChallengeProvided(descriptor, context);

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            descriptor.name(),
            descriptor.suite().value(),
            inline.sharedSecretHex(),
            SecretEncoding.HEX,
            inline.otp(),
            context,
            descriptor.metadata());

    OcraVerificationResult result = inlineVerifier.verifyInline(request);
    int responseDigits = descriptor.suite().cryptoFunction().responseDigits();
    return mapResult(
        result, descriptor.suite().value(), false, descriptor.name(), responseDigits, inline);
  }

  private static VerificationResult mapResult(
      OcraVerificationResult result,
      String suite,
      boolean credentialReference,
      String credentialId,
      int responseDigits,
      NormalizedRequest request) {
    VerificationStatus status =
        switch (result.status()) {
          case MATCH -> VerificationStatus.MATCH;
          case MISMATCH -> VerificationStatus.MISMATCH;
          case INVALID -> VerificationStatus.INVALID;
        };
    VerificationReason reason =
        switch (result.reason()) {
          case MATCH -> VerificationReason.MATCH;
          case STRICT_MISMATCH -> VerificationReason.STRICT_MISMATCH;
          case VALIDATION_FAILURE -> VerificationReason.VALIDATION_FAILURE;
          case CREDENTIAL_NOT_FOUND -> VerificationReason.CREDENTIAL_NOT_FOUND;
          case UNEXPECTED_ERROR -> VerificationReason.UNEXPECTED_ERROR;
        };
    return new VerificationResult(
        status, reason, suite, credentialReference, credentialId, responseDigits, request);
  }

  private void ensureChallengeProvided(
      OcraCredentialDescriptor descriptor, OcraVerificationContext context) {
    boolean requiresChallenge = descriptor.suite().dataInput().challengeQuestion().isPresent();
    if (!requiresChallenge) {
      return;
    }
    if (hasText(context.challenge())
        || hasText(context.clientChallenge())
        || hasText(context.serverChallenge())) {
      return;
    }
    throw new VerificationValidationException(
        "challenge",
        "challenge_required",
        "challengeQuestion required for suite: " + descriptor.suite().value(),
        true);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public interface CredentialResolver {
    Optional<OcraCredentialDescriptor> findById(String credentialId);
  }

  public sealed interface VerificationCommand
      permits VerificationCommand.Stored, VerificationCommand.Inline {
    String otp();

    String challenge();

    String clientChallenge();

    String serverChallenge();

    String sessionHex();

    String pinHashHex();

    String timestampHex();

    Long counter();

    record Stored(
        String credentialId,
        String otp,
        String challenge,
        String clientChallenge,
        String serverChallenge,
        String sessionHex,
        String pinHashHex,
        String timestampHex,
        Long counter)
        implements VerificationCommand {

      public Stored {
        credentialId = Objects.requireNonNull(credentialId, "credentialId").trim();
      }
    }

    record Inline(
        String identifier,
        String suite,
        String sharedSecretHex,
        String otp,
        String challenge,
        String clientChallenge,
        String serverChallenge,
        String sessionHex,
        String pinHashHex,
        String timestampHex,
        Long counter,
        Duration allowedDrift)
        implements VerificationCommand {

      public Inline {
        identifier = identifier == null ? "inline" : identifier.trim();
        suite = Objects.requireNonNull(suite, "suite").trim();
        sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex").trim();
        otp = Objects.requireNonNull(otp, "otp").trim();
      }
    }
  }

  public record VerificationResult(
      VerificationStatus status,
      VerificationReason reason,
      String suite,
      boolean credentialReference,
      String credentialId,
      int responseDigits,
      NormalizedRequest request) {}

  public enum VerificationStatus {
    MATCH,
    MISMATCH,
    INVALID
  }

  public enum VerificationReason {
    MATCH,
    STRICT_MISMATCH,
    VALIDATION_FAILURE,
    CREDENTIAL_NOT_FOUND,
    UNEXPECTED_ERROR
  }

  public static final class VerificationValidationException extends RuntimeException {
    private final String field;
    private final String reasonCode;
    private final boolean sanitized;

    VerificationValidationException(
        String field, String reasonCode, String message, boolean sanitized) {
      super(message);
      this.field = field;
      this.reasonCode = reasonCode;
      this.sanitized = sanitized;
    }

    public String field() {
      return field;
    }

    public String reasonCode() {
      return reasonCode;
    }

    public boolean sanitized() {
      return sanitized;
    }
  }

  public sealed interface NormalizedRequest
      permits NormalizedRequest.Stored, NormalizedRequest.Inline {
    String otp();

    VerificationContext context();

    static NormalizedRequest from(VerificationCommand command) {
      if (command instanceof VerificationCommand.Stored stored) {
        return new Stored(stored);
      }
      if (command instanceof VerificationCommand.Inline inline) {
        return new Inline(inline);
      }
      throw new IllegalArgumentException("Unsupported command: " + command.getClass());
    }

    public static final class Stored implements NormalizedRequest {
      private final String credentialId;
      private final String otp;
      private final VerificationContext context;

      Stored(VerificationCommand.Stored command) {
        this.credentialId = command.credentialId();
        this.otp = command.otp().trim();
        this.context = VerificationContext.from(command);
      }

      public String credentialId() {
        return credentialId;
      }

      @Override
      public String otp() {
        return otp;
      }

      @Override
      public VerificationContext context() {
        return context;
      }
    }

    public static final class Inline implements NormalizedRequest {
      private final String identifier;
      private final String suite;
      private final String sharedSecretHex;
      private final String otp;
      private final VerificationContext context;
      private final Long counter;
      private final String pinHashHex;
      private final Duration allowedDrift;

      Inline(VerificationCommand.Inline command) {
        this.identifier = command.identifier();
        this.suite = command.suite();
        this.sharedSecretHex = normalizeHex(command.sharedSecretHex(), "sharedSecretHex");
        this.otp = command.otp().trim();
        this.context = VerificationContext.from(command);
        this.counter = command.counter();
        this.pinHashHex = normalizeHex(command.pinHashHex(), "pinHashHex");
        this.allowedDrift = command.allowedDrift();
      }

      public String identifier() {
        return identifier;
      }

      public String suite() {
        return suite;
      }

      public String sharedSecretHex() {
        return sharedSecretHex;
      }

      @Override
      public String otp() {
        return otp;
      }

      public Long counter() {
        return counter;
      }

      public String pinHashHex() {
        return pinHashHex;
      }

      public Duration allowedDrift() {
        return allowedDrift;
      }

      @Override
      public VerificationContext context() {
        return context;
      }
    }

    private static String normalizeHex(String value, String field) {
      if (!hasText(value)) {
        return null;
      }
      String trimmed = value.replace(" ", "").trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      if (!trimmed.matches("[0-9A-Fa-f]+")) {
        throw new VerificationValidationException(
            field, field + "_invalid", field + " must be hexadecimal", true);
      }
      return trimmed;
    }
  }

  public record VerificationContext(
      String credentialSource,
      String credentialId,
      String suite,
      String challenge,
      String clientChallenge,
      String serverChallenge,
      String sessionHex,
      String pinHashHex,
      String timestampHex,
      Long counter) {

    static VerificationContext from(VerificationCommand command) {
      return new VerificationContext(
          command instanceof VerificationCommand.Stored ? "stored" : "inline",
          command instanceof VerificationCommand.Stored stored ? stored.credentialId() : null,
          command instanceof VerificationCommand.Inline inline ? inline.suite() : null,
          normalize(command.challenge()),
          normalize(command.clientChallenge()),
          normalize(command.serverChallenge()),
          normalize(command.sessionHex()),
          normalizeHex(command.pinHashHex()),
          normalizeHex(command.timestampHex()),
          command.counter());
    }

    static VerificationContext empty() {
      return new VerificationContext(
          "unknown", null, null, null, null, null, null, null, null, null);
    }

    OcraVerificationContext toCoreContext() {
      return new OcraVerificationContext(
          counter,
          challenge,
          sessionHex,
          clientChallenge,
          serverChallenge,
          pinHashHex,
          timestampHex);
    }

    private static String normalize(String value) {
      if (!hasText(value)) {
        return null;
      }
      return value.trim();
    }

    private static String normalizeHex(String value) {
      if (!hasText(value)) {
        return null;
      }
      return value.replace(" ", "").trim();
    }
  }
}
