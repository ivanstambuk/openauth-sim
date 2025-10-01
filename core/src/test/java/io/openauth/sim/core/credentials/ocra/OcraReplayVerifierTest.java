package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraInlineVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraStoredVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationReason;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationResult;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationStatus;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OcraReplayVerifierTest {

  private static final OcraCredentialFactory FACTORY = new OcraCredentialFactory();
  private static final OcraCredentialPersistenceAdapter PERSISTENCE_ADAPTER =
      new OcraCredentialPersistenceAdapter(new OcraCredentialDescriptorFactory());

  @Test
  @DisplayName("stored credential verification succeeds without mutating persistence state")
  void storedCredentialMatchDoesNotMutateStore() {
    OcraRfc6287VectorFixtures.OneWayVector vector =
        OcraRfc6287VectorFixtures.counterAndPinVectors().get(0);

    String credentialId = "stored-verification";
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraCredentialDescriptor descriptor =
        FACTORY.createDescriptor(
            new OcraCredentialRequest(
                credentialId,
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                SecretEncoding.HEX,
                vector.counter(),
                vector.pinHashHex(),
                null,
                Map.of("source", "test")));

    Credential storedCredential = toCredential(descriptor);
    store.save(storedCredential);

    OcraReplayVerifier verifier = new OcraReplayVerifier(store);

    OcraVerificationContext context =
        new OcraVerificationContext(
            vector.counter(),
            vector.question(),
            vector.sessionInformation(),
            null,
            null,
            vector.pinHashHex(),
            vector.timestampHex());

    OcraStoredVerificationRequest request =
        new OcraStoredVerificationRequest(credentialId, vector.expectedOtp(), context);

    OcraVerificationResult result = verifier.verifyStored(request);

    assertEquals(OcraVerificationStatus.MATCH, result.status());
    assertEquals(OcraVerificationReason.MATCH, result.reason());

    Credential persistedAfter =
        store.findByName(credentialId).orElseThrow(() -> new AssertionError("credential missing"));
    OcraCredentialDescriptor descriptorAfter = toDescriptor(persistedAfter);

    assertEquals(descriptor, descriptorAfter);

    OcraVerificationResult replayResult = verifier.verifyStored(request);
    assertEquals(OcraVerificationStatus.MATCH, replayResult.status());
    assertEquals(OcraVerificationReason.MATCH, replayResult.reason());
  }

  @Test
  @DisplayName("inline credential verification succeeds with identical outcome as stored path")
  void inlineCredentialMatchSucceeds() {
    OcraRfc6287VectorFixtures.OneWayVector vector =
        OcraRfc6287VectorFixtures.standardChallengeQuestionVectors().get(0);

    OcraReplayVerifier verifier = new OcraReplayVerifier(null);

    OcraVerificationContext context =
        new OcraVerificationContext(
            vector.counter(),
            vector.question(),
            vector.sessionInformation(),
            null,
            null,
            vector.pinHashHex(),
            vector.timestampHex());

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            "inline-verification",
            vector.ocraSuite(),
            vector.sharedSecretHex(),
            SecretEncoding.HEX,
            vector.expectedOtp(),
            context,
            Map.of("source", "test-inline"));

    OcraVerificationResult result = verifier.verifyInline(request);

    assertEquals(OcraVerificationStatus.MATCH, result.status());
    assertEquals(OcraVerificationReason.MATCH, result.reason());
  }

  @Test
  @DisplayName("inline verification accepts null metadata and still matches")
  void inlineVerificationAllowsNullMetadata() {
    OcraRfc6287VectorFixtures.OneWayVector vector =
        OcraRfc6287VectorFixtures.standardChallengeQuestionVectors().get(1);

    OcraReplayVerifier verifier = new OcraReplayVerifier(null);

    OcraVerificationContext context =
        new OcraVerificationContext(
            vector.counter(),
            vector.question(),
            vector.sessionInformation(),
            null,
            null,
            vector.pinHashHex(),
            vector.timestampHex());

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            "inline-null-metadata",
            vector.ocraSuite(),
            vector.sharedSecretHex(),
            SecretEncoding.HEX,
            vector.expectedOtp(),
            context,
            null);

    OcraVerificationResult result = verifier.verifyInline(request);

    assertEquals(OcraVerificationStatus.MATCH, result.status());
    assertEquals(OcraVerificationReason.MATCH, result.reason());
  }

  @Test
  @DisplayName("strict mismatch returned when supplied counter differs from stored descriptor")
  void storedCredentialStrictMismatchWhenCounterDiffers() {
    OcraRfc6287VectorFixtures.OneWayVector vector =
        OcraRfc6287VectorFixtures.counterAndPinVectors().get(0);

    String credentialId = "stored-strict-mismatch";
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraCredentialDescriptor descriptor =
        FACTORY.createDescriptor(
            new OcraCredentialRequest(
                credentialId,
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                SecretEncoding.HEX,
                vector.counter(),
                vector.pinHashHex(),
                null,
                Map.of("source", "test")));

    store.save(toCredential(descriptor));

    OcraReplayVerifier verifier = new OcraReplayVerifier(store);

    OcraVerificationContext mismatchedContext =
        new OcraVerificationContext(
            vector.counter() == null ? 1L : vector.counter() + 1,
            vector.question(),
            vector.sessionInformation(),
            null,
            null,
            vector.pinHashHex(),
            vector.timestampHex());

    OcraStoredVerificationRequest request =
        new OcraStoredVerificationRequest(credentialId, vector.expectedOtp(), mismatchedContext);

    OcraVerificationResult result = verifier.verifyStored(request);

    assertEquals(OcraVerificationStatus.MISMATCH, result.status());
    assertEquals(OcraVerificationReason.STRICT_MISMATCH, result.reason());

    Credential persistedAfter =
        store.findByName(credentialId).orElseThrow(() -> new AssertionError("credential missing"));
    OcraCredentialDescriptor descriptorAfter = toDescriptor(persistedAfter);
    assertEquals(descriptor, descriptorAfter);
  }

  @Test
  @DisplayName("stored verification returns validation failure when challenge missing")
  void storedVerificationMissingChallengeFails() {
    OcraRfc6287VectorFixtures.OneWayVector vector =
        OcraRfc6287VectorFixtures.counterAndPinVectors().get(0);

    String credentialId = "stored-missing-challenge";
    InMemoryCredentialStore store = new InMemoryCredentialStore();
    OcraCredentialDescriptor descriptor =
        FACTORY.createDescriptor(
            new OcraCredentialRequest(
                credentialId,
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                SecretEncoding.HEX,
                vector.counter(),
                vector.pinHashHex(),
                null,
                Map.of("source", "test")));

    store.save(toCredential(descriptor));

    OcraReplayVerifier verifier = new OcraReplayVerifier(store);

    OcraVerificationContext context =
        new OcraVerificationContext(
            vector.counter(), null, null, null, null, vector.pinHashHex(), vector.timestampHex());

    OcraVerificationResult result =
        verifier.verifyStored(
            new OcraStoredVerificationRequest(credentialId, vector.expectedOtp(), context));

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.VALIDATION_FAILURE, result.reason());
  }

  @Test
  @DisplayName("inline verification reports strict mismatch for incorrect OTP")
  void inlineVerificationStrictMismatch() {
    OcraVerificationContext context =
        new OcraVerificationContext(null, "12345678", null, null, null, null, null);

    OcraReplayVerifier verifier = new OcraReplayVerifier(null);

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            "inline-mismatch",
            "OCRA-1:HOTP-SHA1-6:QN08",
            OcraRfc6287VectorFixtures.STANDARD_KEY_20,
            SecretEncoding.HEX,
            "999999",
            context,
            Map.of("source", "test-inline"));

    OcraVerificationResult result = verifier.verifyInline(request);

    assertEquals(OcraVerificationStatus.MISMATCH, result.status());
    assertEquals(OcraVerificationReason.STRICT_MISMATCH, result.reason());
  }

  @Test
  @DisplayName("stored verification returns validation failure when OTP missing")
  void storedVerificationRequiresOtp() {
    OcraReplayVerifier verifier = new OcraReplayVerifier(new EmptyCredentialStore());

    OcraVerificationContext context =
        new OcraVerificationContext(0L, "12345678", null, null, null, null, null);

    OcraVerificationResult result =
        verifier.verifyStored(new OcraStoredVerificationRequest("missing", "  ", context));

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.VALIDATION_FAILURE, result.reason());
  }

  @Test
  @DisplayName("stored verification surfaces unexpected errors from persistence layer")
  void storedVerificationUnexpectedError() {
    OcraRfc6287VectorFixtures.OneWayVector vector =
        OcraRfc6287VectorFixtures.counterAndPinVectors().get(0);

    OcraCredentialDescriptor descriptor =
        FACTORY.createDescriptor(
            new OcraCredentialRequest(
                "unexpected-store",
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                SecretEncoding.HEX,
                vector.counter(),
                vector.pinHashHex(),
                null,
                Map.of("source", "test")));

    Credential valid = toCredential(descriptor);

    Map<String, String> corruptedAttributes = new HashMap<>(valid.attributes());
    corruptedAttributes.put(OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX + "source", null);
    Credential corrupted =
        new Credential(
            valid.name(),
            valid.type(),
            valid.secret(),
            corruptedAttributes,
            valid.createdAt(),
            valid.updatedAt());

    OcraReplayVerifier verifier = new OcraReplayVerifier(new SingleCredentialStore(corrupted));

    OcraVerificationContext context =
        new OcraVerificationContext(
            vector.counter(),
            vector.question(),
            vector.sessionInformation(),
            null,
            null,
            vector.pinHashHex(),
            vector.timestampHex());

    OcraVerificationResult result =
        verifier.verifyStored(
            new OcraStoredVerificationRequest("unexpected-store", vector.expectedOtp(), context));

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.UNEXPECTED_ERROR, result.reason());
  }

  @Test
  @DisplayName("stored verification returns credential_not_found when store unavailable")
  void storedVerificationWithoutStore() {
    OcraReplayVerifier verifier = new OcraReplayVerifier(null);

    OcraVerificationContext context =
        new OcraVerificationContext(0L, "12345678", null, null, null, null, null);

    OcraVerificationResult result =
        verifier.verifyStored(new OcraStoredVerificationRequest("alpha", "123456", context));

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.CREDENTIAL_NOT_FOUND, result.reason());
  }

  @Test
  @DisplayName("inline verification requires suite identifier")
  void inlineVerificationRequiresSuite() {
    OcraReplayVerifier verifier = new OcraReplayVerifier(null);
    OcraVerificationContext context =
        new OcraVerificationContext(null, "12345678", null, null, null, null, null);

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            "inline-missing-suite",
            "  ",
            OcraRfc6287VectorFixtures.STANDARD_KEY_20,
            SecretEncoding.HEX,
            "123456",
            context,
            Map.of("source", "test-inline"));

    OcraVerificationResult result = verifier.verifyInline(request);

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.VALIDATION_FAILURE, result.reason());
  }

  @Test
  @DisplayName("stored verification returns credential_not_found when descriptor missing")
  void storedVerificationCredentialMissing() {
    OcraReplayVerifier verifier = new OcraReplayVerifier(new EmptyCredentialStore());

    OcraVerificationContext context =
        new OcraVerificationContext(0L, "12345678", null, null, null, null, null);

    OcraVerificationResult result =
        verifier.verifyStored(new OcraStoredVerificationRequest("missing", "000000", context));

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.CREDENTIAL_NOT_FOUND, result.reason());
  }

  @Test
  @DisplayName(
      "inline verification fails when timestamp supplied for suite without timestamp input")
  void inlineVerificationRejectsTimestampWhenNotPermitted() {
    OcraVerificationContext context =
        new OcraVerificationContext(null, "12345678", null, null, null, null, "00FF");

    OcraReplayVerifier verifier = new OcraReplayVerifier(null);

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            "inline-no-timestamp",
            "OCRA-1:HOTP-SHA1-6:QN08",
            OcraRfc6287VectorFixtures.STANDARD_KEY_20,
            SecretEncoding.HEX,
            "000000",
            context,
            Map.of("source", "test-inline"));

    OcraVerificationResult result = verifier.verifyInline(request);

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.VALIDATION_FAILURE, result.reason());
  }

  @Test
  @DisplayName("inline verification requires timestamp when suite expects it")
  void inlineVerificationRequiresTimestamp() {
    OcraVerificationContext context =
        new OcraVerificationContext(null, "12345678", null, null, null, null, null);

    OcraReplayVerifier verifier = new OcraReplayVerifier(null);

    OcraInlineVerificationRequest request =
        new OcraInlineVerificationRequest(
            "inline-timestamp",
            "OCRA-1:HOTP-SHA1-6:QA08-T1",
            OcraRfc6287VectorFixtures.STANDARD_KEY_20,
            SecretEncoding.HEX,
            "000000",
            context,
            Map.of("source", "test-inline"));

    OcraVerificationResult result = verifier.verifyInline(request);

    assertEquals(OcraVerificationStatus.INVALID, result.status());
    assertEquals(OcraVerificationReason.VALIDATION_FAILURE, result.reason());
  }

  private static OcraCredentialDescriptor toDescriptor(Credential credential) {
    VersionedCredentialRecord record = VersionedCredentialRecordMapper.toRecord(credential);
    return PERSISTENCE_ADAPTER.deserialize(record);
  }

  private static Credential toCredential(OcraCredentialDescriptor descriptor) {
    VersionedCredentialRecord record = PERSISTENCE_ADAPTER.serialize(descriptor);
    return VersionedCredentialRecordMapper.toCredential(record);
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, Credential> storage = new HashMap<>();

    @Override
    public void save(Credential credential) {
      storage.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(storage.get(name));
    }

    @Override
    public List<Credential> findAll() {
      return new ArrayList<>(storage.values());
    }

    @Override
    public boolean delete(String name) {
      return storage.remove(name) != null;
    }

    @Override
    public void close() {
      storage.clear();
    }
  }

  private static final class EmptyCredentialStore implements CredentialStore {

    @Override
    public void save(Credential credential) {
      // no-op
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.empty();
    }

    @Override
    public List<Credential> findAll() {
      return List.of();
    }

    @Override
    public boolean delete(String name) {
      return false;
    }

    @Override
    public void close() {
      // no-op
    }
  }

  private static final class SingleCredentialStore implements CredentialStore {
    private final Credential credential;

    private SingleCredentialStore(Credential credential) {
      this.credential = credential;
    }

    @Override
    public void save(Credential credential) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.of(credential);
    }

    @Override
    public List<Credential> findAll() {
      return List.of(credential);
    }

    @Override
    public boolean delete(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
