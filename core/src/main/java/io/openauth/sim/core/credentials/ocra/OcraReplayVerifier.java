package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Core service responsible for replaying OCRA verifications without mutating state. */
public final class OcraReplayVerifier {

  private static final Logger LOGGER =
      Logger.getLogger("io.openauth.sim.core.credentials.ocra.replay");

  private final CredentialStore credentialStore;
  private final OcraCredentialFactory credentialFactory;
  private final OcraCredentialPersistenceAdapter persistenceAdapter;

  public OcraReplayVerifier(CredentialStore credentialStore) {
    this(credentialStore, new OcraCredentialFactory(), new OcraCredentialPersistenceAdapter());
  }

  OcraReplayVerifier(
      CredentialStore credentialStore,
      OcraCredentialFactory credentialFactory,
      OcraCredentialPersistenceAdapter persistenceAdapter) {
    this.credentialStore = credentialStore;
    this.credentialFactory = Objects.requireNonNull(credentialFactory, "credentialFactory");
    this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
  }

  public OcraVerificationResult verifyStored(OcraStoredVerificationRequest request) {
    Objects.requireNonNull(request, "request");

    if (!hasText(request.credentialId()) || !hasText(request.otp())) {
      return invalid(OcraVerificationReason.VALIDATION_FAILURE);
    }
    if (credentialStore == null) {
      return invalid(OcraVerificationReason.CREDENTIAL_NOT_FOUND);
    }

    Optional<Credential> credential = credentialStore.findByName(request.credentialId().trim());
    if (credential.isEmpty()) {
      return invalid(OcraVerificationReason.CREDENTIAL_NOT_FOUND);
    }

    try {
      OcraCredentialDescriptor descriptor =
          persistenceAdapter.deserialize(
              VersionedCredentialRecordMapper.toRecord(credential.get()));
      return performVerification(descriptor, request.context(), request.otp());
    } catch (IllegalArgumentException ex) {
      logValidation(ex);
      return invalid(OcraVerificationReason.VALIDATION_FAILURE);
    } catch (RuntimeException ex) {
      logUnexpected(ex);
      return invalid(OcraVerificationReason.UNEXPECTED_ERROR);
    }
  }

  public OcraVerificationResult verifyInline(OcraInlineVerificationRequest request) {
    Objects.requireNonNull(request, "request");

    if (!hasText(request.descriptorName())
        || !hasText(request.suite())
        || !hasText(request.sharedSecretHex())
        || !hasText(request.otp())) {
      return invalid(OcraVerificationReason.VALIDATION_FAILURE);
    }

    try {
      Map<String, String> metadata = request.metadata() == null ? Map.of() : request.metadata();
      OcraCredentialFactory.OcraCredentialRequest descriptorRequest =
          new OcraCredentialFactory.OcraCredentialRequest(
              request.descriptorName().trim(),
              request.suite().trim(),
              request.sharedSecretHex().trim(),
              request.sharedSecretEncoding(),
              request.context().counter(),
              request.context().pinHashHex(),
              null,
              metadata);

      OcraCredentialDescriptor descriptor = credentialFactory.createDescriptor(descriptorRequest);
      return performVerification(descriptor, request.context(), request.otp());
    } catch (IllegalArgumentException ex) {
      logValidation(ex);
      return invalid(OcraVerificationReason.VALIDATION_FAILURE);
    } catch (RuntimeException ex) {
      logUnexpected(ex);
      return invalid(OcraVerificationReason.UNEXPECTED_ERROR);
    }
  }

  public record OcraStoredVerificationRequest(
      String credentialId, String otp, OcraVerificationContext context) {

    public OcraStoredVerificationRequest {
      Objects.requireNonNull(credentialId, "credentialId");
      Objects.requireNonNull(otp, "otp");
      Objects.requireNonNull(context, "context");
    }
  }

  public record OcraInlineVerificationRequest(
      String descriptorName,
      String suite,
      String sharedSecretHex,
      SecretEncoding sharedSecretEncoding,
      String otp,
      OcraVerificationContext context,
      Map<String, String> metadata) {

    public OcraInlineVerificationRequest {
      Objects.requireNonNull(descriptorName, "descriptorName");
      Objects.requireNonNull(suite, "suite");
      Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
      Objects.requireNonNull(sharedSecretEncoding, "sharedSecretEncoding");
      Objects.requireNonNull(otp, "otp");
      Objects.requireNonNull(context, "context");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  public record OcraVerificationContext(
      Long counter,
      String challenge,
      String sessionInformation,
      String clientChallenge,
      String serverChallenge,
      String pinHashHex,
      String timestampHex) {

    // Marker type for passing immutable verification state.
  }

  public record OcraVerificationResult(
      OcraVerificationStatus status, OcraVerificationReason reason) {

    public OcraVerificationResult {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(reason, "reason");
    }
  }

  public enum OcraVerificationStatus {
    MATCH,
    MISMATCH,
    INVALID
  }

  public enum OcraVerificationReason {
    MATCH,
    STRICT_MISMATCH,
    VALIDATION_FAILURE,
    CREDENTIAL_NOT_FOUND,
    UNEXPECTED_ERROR
  }

  private OcraVerificationResult performVerification(
      OcraCredentialDescriptor descriptor, OcraVerificationContext context, String suppliedOtp) {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(context, "context");

    String otp = suppliedOtp == null ? null : suppliedOtp.trim();
    if (!hasText(otp)) {
      return invalid(OcraVerificationReason.VALIDATION_FAILURE);
    }

    try {
      String challenge = normalize(context.challenge());
      String sessionInformation = normalize(context.sessionInformation());
      String clientChallenge = normalize(context.clientChallenge());
      String serverChallenge = normalize(context.serverChallenge());
      String pinHashHex = normalize(context.pinHashHex());
      String timestampHex = normalize(context.timestampHex());

      credentialFactory.validateChallenge(descriptor, challenge);
      credentialFactory.validateSessionInformation(descriptor, sessionInformation);
      validateTimestamp(descriptor, timestampHex);

      OcraResponseCalculator.OcraExecutionContext executionContext =
          new OcraResponseCalculator.OcraExecutionContext(
              context.counter(),
              challenge,
              sessionInformation,
              clientChallenge,
              serverChallenge,
              pinHashHex,
              timestampHex);

      String expectedOtp = OcraResponseCalculator.generate(descriptor, executionContext);
      if (expectedOtp.equals(otp)) {
        return new OcraVerificationResult(
            OcraVerificationStatus.MATCH, OcraVerificationReason.MATCH);
      }
      return new OcraVerificationResult(
          OcraVerificationStatus.MISMATCH, OcraVerificationReason.STRICT_MISMATCH);
    } catch (IllegalArgumentException ex) {
      logValidation(ex);
      return invalid(OcraVerificationReason.VALIDATION_FAILURE);
    } catch (RuntimeException ex) {
      logUnexpected(ex);
      return invalid(OcraVerificationReason.UNEXPECTED_ERROR);
    }
  }

  private static void validateTimestamp(OcraCredentialDescriptor descriptor, String timestampHex) {
    boolean expectsTimestamp = descriptor.suite().dataInput().timestamp().isPresent();
    if (!expectsTimestamp) {
      if (hasText(timestampHex)) {
        throw new IllegalArgumentException(
            "timestampHex not permitted for suite: " + descriptor.suite().value());
      }
      return;
    }

    if (!hasText(timestampHex)) {
      throw new IllegalArgumentException(
          "timestampHex required for suite: " + descriptor.suite().value());
    }
  }

  private static OcraVerificationResult invalid(OcraVerificationReason reason) {
    return new OcraVerificationResult(OcraVerificationStatus.INVALID, reason);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim();
  }

  private static void logValidation(Exception ex) {
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.log(Level.FINE, "OCRA replay validation failure", ex);
    }
  }

  private static void logUnexpected(Exception ex) {
    LOGGER.log(Level.WARNING, "Unexpected error during OCRA replay verification", ex);
  }
}
