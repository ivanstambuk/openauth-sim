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

  private static final String CANONICAL_ID_SHA1 = "ui-totp-demo";
  private static final String CANONICAL_ID_SHA512 = "ui-totp-demo-sha512";
  private static final SecretMaterial CANONICAL_SECRET =
      SecretMaterial.fromStringUtf8("1234567890123456789012");

  private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();
  private final CapturingCredentialStore credentialStore = new CapturingCredentialStore();

  private TotpSeedApplicationService.SeedCommand sha1SeedCommand() {
    return new TotpSeedApplicationService.SeedCommand(
        CANONICAL_ID_SHA1,
        CANONICAL_SECRET.asHex(),
        TotpHashAlgorithm.SHA1,
        6,
        Duration.ofSeconds(30),
        TotpDriftWindow.of(1, 1),
        Map.of("ui.label", "TOTP demo credential (SHA1, 6 digits, 30s step)"));
  }

  private TotpSeedApplicationService.SeedCommand sha512SeedCommand() {
    return new TotpSeedApplicationService.SeedCommand(
        CANONICAL_ID_SHA512,
        CANONICAL_SECRET.asHex(),
        TotpHashAlgorithm.SHA512,
        8,
        Duration.ofSeconds(60),
        TotpDriftWindow.of(2, 2),
        Map.of("ui.label", "TOTP demo credential (SHA512, 8 digits, 60s step)"));
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
        service.seed(List.of(sha1SeedCommand(), sha512SeedCommand()), credentialStore);

    List<String> expectedIds = List.of(CANONICAL_ID_SHA1, CANONICAL_ID_SHA512);
    assertEquals(
        expectedIds.stream().sorted().toList(),
        result.addedCredentialIds().stream().sorted().toList());

    List<Credential> persisted = credentialStore.findAll();
    assertEquals(2, persisted.size());

    Credential sha1Credential = credentialStore.findByName(CANONICAL_ID_SHA1).orElseThrow();
    assertEquals(CredentialType.OATH_TOTP, sha1Credential.type());
    assertEquals("SHA1", sha1Credential.attributes().get("totp.algorithm"));
    assertEquals("6", sha1Credential.attributes().get("totp.digits"));
    assertEquals("30", sha1Credential.attributes().get("totp.stepSeconds"));
    assertEquals("1", sha1Credential.attributes().get("totp.drift.backward"));
    assertEquals("1", sha1Credential.attributes().get("totp.drift.forward"));

    Credential sha512Credential = credentialStore.findByName(CANONICAL_ID_SHA512).orElseThrow();
    assertEquals(CredentialType.OATH_TOTP, sha512Credential.type());
    assertEquals("SHA512", sha512Credential.attributes().get("totp.algorithm"));
    assertEquals("8", sha512Credential.attributes().get("totp.digits"));
    assertEquals("60", sha512Credential.attributes().get("totp.stepSeconds"));
    assertEquals("2", sha512Credential.attributes().get("totp.drift.backward"));
    assertEquals("2", sha512Credential.attributes().get("totp.drift.forward"));

    assertIterableEquals(
        List.of(
            Operation.save(CANONICAL_ID_SHA1, CredentialType.OATH_TOTP),
            Operation.save(CANONICAL_ID_SHA512, CredentialType.OATH_TOTP)),
        credentialStore.operations());
  }

  @Test
  @DisplayName("Skips seeding when canonical identifiers already exist")
  void skipsExistingCanonicalCredentials() {
    credentialStore.save(
        serializeDescriptor(
            TotpDescriptor.create(
                CANONICAL_ID_SHA1,
                CANONICAL_SECRET,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1))));
    credentialStore.resetOperations();

    TotpSeedApplicationService service = new TotpSeedApplicationService();
    TotpSeedApplicationService.SeedResult result =
        service.seed(List.of(sha1SeedCommand(), sha512SeedCommand()), credentialStore);

    assertEquals(List.of(CANONICAL_ID_SHA512), result.addedCredentialIds());
    assertIterableEquals(
        List.of(Operation.save(CANONICAL_ID_SHA512, CredentialType.OATH_TOTP)),
        credentialStore.operations());
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
}
