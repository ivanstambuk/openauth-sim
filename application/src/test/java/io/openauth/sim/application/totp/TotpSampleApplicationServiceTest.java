package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TotpSampleApplicationServiceTest {

  private static final String CREDENTIAL_ID = "totp-sample";
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("3132333435363738393031323334353637383930");
  private static final TotpHashAlgorithm ALGORITHM = TotpHashAlgorithm.SHA256;
  private static final int DIGITS = 8;
  private static final Duration STEP_DURATION = Duration.ofSeconds(60);
  private static final TotpDriftWindow DRIFT_WINDOW = TotpDriftWindow.of(2, 3);
  private static final long SAMPLE_TIMESTAMP = 1_701_001_001L;

  private TotpSampleApplicationService service;
  private InMemoryCredentialStore store;

  @BeforeEach
  void setUp() {
    store = new InMemoryCredentialStore();
    service = new TotpSampleApplicationService(store);
  }

  @Test
  @DisplayName("Stored TOTP sample returns deterministic OTP/timestamp and preserves metadata")
  void storedSampleReturnsDeterministicPayload() {
    Credential storedCredential = totpCredential(STEP_DURATION, DRIFT_WINDOW, metadata());
    store.save(storedCredential);

    Optional<TotpSampleApplicationService.StoredSample> response =
        service.storedSample(CREDENTIAL_ID);

    assertTrue(response.isPresent(), "Expected stored sample payload");
    TotpSampleApplicationService.StoredSample sample = response.orElseThrow();
    assertEquals(CREDENTIAL_ID, sample.credentialId());
    assertEquals(ALGORITHM, sample.algorithm());
    assertEquals(DIGITS, sample.digits());
    assertEquals(STEP_DURATION.toSeconds(), sample.stepSeconds());
    assertEquals(DRIFT_WINDOW.backwardSteps(), sample.driftBackwardSteps());
    assertEquals(DRIFT_WINDOW.forwardSteps(), sample.driftForwardSteps());
    assertEquals(SAMPLE_TIMESTAMP, sample.timestampEpochSeconds());

    TotpDescriptor descriptor =
        TotpDescriptor.create(
            CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS, STEP_DURATION, DRIFT_WINDOW);
    String expectedOtp =
        TotpGenerator.generate(descriptor, Instant.ofEpochSecond(SAMPLE_TIMESTAMP));
    assertEquals(expectedOtp, sample.otp(), "Sample should include deterministic OTP");

    assertEquals("inline-ui-totp-demo", sample.metadata().get("samplePresetKey"));
    assertEquals("SHA-1, 6 digits, 30s", sample.metadata().get("samplePresetLabel"));
    assertEquals("Seeded TOTP credential (test fixture)", sample.metadata().get("notes"));

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals(
        storedCredential.attributes(),
        persisted.attributes(),
        "Sample lookup must not mutate stored credential metadata");
  }

  @Test
  @DisplayName("Stored TOTP sample returns empty when credential is missing")
  void storedSampleMissingReturnsEmpty() {
    assertTrue(service.storedSample("missing").isEmpty());
  }

  @Test
  @DisplayName("Stored TOTP sample skips non-TOTP credentials")
  void storedSampleSkipsNonTotpCredentials() {
    Credential ocraCredential =
        new Credential(
            "ocra-demo",
            CredentialType.OATH_OCRA,
            SECRET,
            Map.of("ocra.metadata.label", "OCRA Sample"),
            Instant.now(),
            Instant.now());
    store.save(ocraCredential);

    assertTrue(service.storedSample("ocra-demo").isEmpty());
  }

  private Credential totpCredential(
      Duration stepDuration, TotpDriftWindow driftWindow, Map<String, String> metadata) {
    TotpDescriptor descriptor =
        TotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS, stepDuration, driftWindow);
    TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();
    Credential serialized =
        VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
    Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
    metadata.forEach((key, value) -> attributes.put("totp.metadata." + key, value));
    return new Credential(
        serialized.name(),
        serialized.type(),
        serialized.secret(),
        attributes,
        serialized.createdAt(),
        serialized.updatedAt());
  }

  private Map<String, String> metadata() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("presetKey", "inline-ui-totp-demo");
    metadata.put("presetLabel", "SHA-1, 6 digits, 30s");
    metadata.put("notes", "Seeded TOTP credential (test fixture)");
    metadata.put("sampleTimestamp", Long.toString(SAMPLE_TIMESTAMP));
    return metadata;
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final ConcurrentHashMap<String, Credential> credentials = new ConcurrentHashMap<>();

    @Override
    public void save(Credential credential) {
      credentials.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(credentials.get(name));
    }

    @Override
    public List<Credential> findAll() {
      return new ArrayList<>(credentials.values());
    }

    @Override
    public boolean delete(String name) {
      return credentials.remove(name) != null;
    }

    @Override
    public void close() {
      credentials.clear();
    }
  }
}
