package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures.TotpJsonVector;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Failing specification for the TOTP seeding application service. The suite asserts that canonical
 * credentials are inserted when absent and skipped when already present. It will pass once {@link
 * TotpSeedApplicationService} mirrors the HOTP seeding workflow.
 */
final class TotpSeedApplicationServiceTest {

  private static final String SECRET_SHA1_DEMO_HEX = "31323334353637383930313233343536373839303132";
  private static final TotpJsonVector VECTOR_SHA1_DIGITS8_T59 = vector("rfc6238_sha1_digits8_t59");
  private static final TotpJsonVector VECTOR_SHA256_DIGITS6_T59 =
      vector("rfc6238_sha256_digits6_t59");
  private static final TotpJsonVector VECTOR_SHA256_DIGITS8_T59 =
      vector("rfc6238_sha256_digits8_t59");
  private static final TotpJsonVector VECTOR_SHA512_DIGITS6_T59 =
      vector("rfc6238_sha512_digits6_t59");
  private static final TotpJsonVector VECTOR_SHA512_DIGITS8_T59 =
      vector("rfc6238_sha512_digits8_t59");

  private static final List<SeedSample> CANONICAL_SAMPLES =
      List.of(
          new SeedSample(
              "ui-totp-sample-sha1-6", SECRET_SHA1_DEMO_HEX, TotpHashAlgorithm.SHA1, 6, 30, 1, 1),
          fromVector("ui-totp-sample-sha1-8", VECTOR_SHA1_DIGITS8_T59),
          fromVector("ui-totp-sample-sha256-6", VECTOR_SHA256_DIGITS6_T59),
          fromVector("ui-totp-sample-sha256-8", VECTOR_SHA256_DIGITS8_T59),
          fromVector("ui-totp-sample-sha512-6", VECTOR_SHA512_DIGITS6_T59),
          fromVector("ui-totp-sample-sha512-8", VECTOR_SHA512_DIGITS8_T59));

  private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();
  private final CapturingCredentialStore credentialStore = new CapturingCredentialStore();

  private List<TotpSeedApplicationService.SeedCommand> canonicalCommands() {
    return CANONICAL_SAMPLES.stream().map(this::toSeedCommand).toList();
  }

  private TotpSeedApplicationService.SeedCommand toSeedCommand(SeedSample sample) {
    Map<String, String> metadata =
        Map.of("presetKey", sample.credentialId(), "presetLabel", sample.credentialId());
    return new TotpSeedApplicationService.SeedCommand(
        sample.credentialId(),
        sample.sharedSecretHex(),
        sample.algorithm(),
        sample.digits(),
        Duration.ofSeconds(sample.stepSeconds()),
        TotpDriftWindow.of(sample.driftBackward(), sample.driftForward()),
        metadata);
  }

  @BeforeEach
  void resetStore() {
    credentialStore.reset();
  }

  @Test
  @DisplayName("Seeds canonical TOTP credentials when missing")
  void seedsCanonicalTotpCredentials() {
    TotpSeedApplicationService service = new TotpSeedApplicationService();

    TotpSeedApplicationService.SeedResult result =
        service.seed(canonicalCommands(), credentialStore);

    List<String> expectedIds =
        CANONICAL_SAMPLES.stream().map(SeedSample::credentialId).sorted().toList();
    assertEquals(expectedIds, result.addedCredentialIds().stream().sorted().toList());

    List<Credential> persisted = credentialStore.findAll();
    assertEquals(CANONICAL_SAMPLES.size(), persisted.size());

    for (SeedSample sample : CANONICAL_SAMPLES) {
      Credential credential = credentialStore.findByName(sample.credentialId()).orElseThrow();
      Map<String, String> attributes = credential.attributes();
      assertEquals(CredentialType.OATH_TOTP, credential.type());
      assertEquals(sample.algorithm().name(), attributes.get("totp.algorithm"));
      assertEquals(Integer.toString(sample.digits()), attributes.get("totp.digits"));
      assertEquals(Long.toString(sample.stepSeconds()), attributes.get("totp.stepSeconds"));
      assertEquals(Integer.toString(sample.driftBackward()), attributes.get("totp.drift.backward"));
      assertEquals(Integer.toString(sample.driftForward()), attributes.get("totp.drift.forward"));
    }

    assertIterableEquals(
        CANONICAL_SAMPLES.stream()
            .map(sample -> Operation.save(sample.credentialId(), CredentialType.OATH_TOTP))
            .toList(),
        credentialStore.operations());
  }

  @Test
  @DisplayName("Skips seeding when canonical identifiers already exist")
  void skipsExistingCanonicalCredentials() {
    SeedSample existing = CANONICAL_SAMPLES.get(0);
    credentialStore.save(serializeSample(existing));
    credentialStore.resetOperations();

    TotpSeedApplicationService service = new TotpSeedApplicationService();
    TotpSeedApplicationService.SeedResult result =
        service.seed(canonicalCommands(), credentialStore);

    List<String> expectedIds =
        CANONICAL_SAMPLES.stream()
            .map(SeedSample::credentialId)
            .filter(id -> !id.equals(existing.credentialId()))
            .sorted()
            .toList();
    assertEquals(expectedIds, result.addedCredentialIds().stream().sorted().toList());
    assertIterableEquals(
        CANONICAL_SAMPLES.stream()
            .filter(sample -> !sample.equals(existing))
            .map(sample -> Operation.save(sample.credentialId(), CredentialType.OATH_TOTP))
            .toList(),
        credentialStore.operations());
  }

  private Credential serializeSample(SeedSample sample) {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            sample.credentialId(),
            SecretMaterial.fromHex(sample.sharedSecretHex()),
            sample.algorithm(),
            sample.digits(),
            Duration.ofSeconds(sample.stepSeconds()),
            TotpDriftWindow.of(sample.driftBackward(), sample.driftForward()));
    return serializeDescriptor(descriptor);
  }

  private Credential serializeDescriptor(TotpDescriptor descriptor) {
    return VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
  }

  private static final class CapturingCredentialStore implements CredentialStore {

    private final LinkedHashMap<String, Credential> store = new LinkedHashMap<>();
    private final List<Operation> operations = new ArrayList<>();

    void reset() {
      store.clear();
      operations.clear();
    }

    void resetOperations() {
      operations.clear();
    }

    List<Operation> operations() {
      return List.copyOf(operations);
    }

    @Override
    public void save(Credential credential) {
      store.put(credential.name(), credential);
      operations.add(Operation.save(credential.name(), credential.type()));
    }

    @Override
    public java.util.Optional<Credential> findByName(String name) {
      return java.util.Optional.ofNullable(store.get(name));
    }

    @Override
    public List<Credential> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public boolean delete(String name) {
      boolean removed = store.remove(name) != null;
      if (removed) {
        operations.add(Operation.delete(name));
      }
      return removed;
    }

    @Override
    public void close() {
      // no-op for in-memory store
    }
  }

  private record Operation(OperationType type, String credentialId, CredentialType credentialType) {

    static Operation save(String credentialId, CredentialType credentialType) {
      return new Operation(OperationType.SAVE, credentialId, credentialType);
    }

    static Operation delete(String credentialId) {
      return new Operation(OperationType.DELETE, credentialId, null);
    }
  }

  private enum OperationType {
    SAVE,
    DELETE
  }

  private record SeedSample(
      String credentialId,
      String sharedSecretHex,
      TotpHashAlgorithm algorithm,
      int digits,
      long stepSeconds,
      int driftBackward,
      int driftForward) {
    // Marker record describing canonical seed expectations.
  }

  private static SeedSample fromVector(String credentialId, TotpJsonVector vector) {
    return new SeedSample(
        credentialId,
        vector.secret().asHex(),
        vector.algorithm(),
        vector.digits(),
        vector.stepSeconds(),
        vector.driftBackwardSteps(),
        vector.driftForwardSteps());
  }

  private static TotpJsonVector vector(String id) {
    return TotpJsonVectorFixtures.getById(id);
  }
}
