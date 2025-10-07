package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
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

final class HotpSampleApplicationServiceTest {

  private static final String CREDENTIAL_ID = "hotp-sample";
  private static final HotpHashAlgorithm ALGORITHM = HotpHashAlgorithm.SHA1;
  private static final int DIGITS = 6;
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("3132333435363738393031323334353637383930");

  private InMemoryCredentialStore store;
  private HotpSampleApplicationService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryCredentialStore();
    service = new HotpSampleApplicationService(store);
  }

  @Test
  @DisplayName("stored sample returns current counter and matching OTP without mutation")
  void storedSampleReturnsMatchingOtp() {
    store.save(credential(ALGORITHM, DIGITS, 9L, metadata("ui-hotp-demo", "stored-demo")));

    Optional<HotpSampleApplicationService.StoredSample> response =
        service.storedSample(CREDENTIAL_ID);

    assertTrue(response.isPresent());
    HotpSampleApplicationService.StoredSample sample = response.orElseThrow();
    assertEquals(CREDENTIAL_ID, sample.credentialId());
    assertEquals(ALGORITHM, sample.algorithm());
    assertEquals(DIGITS, sample.digits());
    assertEquals(9L, sample.counter());
    assertEquals(generateOtp(ALGORITHM, DIGITS, 9L), sample.otp());
    assertEquals("ui-hotp-demo", sample.metadata().get("samplePresetKey"));
    assertEquals("stored-demo", sample.metadata().get("samplePresetLabel"));

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals("9", persisted.attributes().get("hotp.counter"));
  }

  @Test
  @DisplayName("stored sample returns empty when credential missing")
  void storedSampleMissingReturnsEmpty() {
    assertTrue(service.storedSample("missing").isEmpty());
  }

  @Test
  @DisplayName("stored sample skips non-HOTP credentials")
  void storedSampleSkipsNonHotp() {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("hotp.counter", "0");
    Credential credential =
        new Credential(
            "ocra-reference",
            CredentialType.OATH_OCRA,
            SECRET,
            attributes,
            Instant.now(),
            Instant.now());
    store.save(credential);

    assertTrue(service.storedSample("ocra-reference").isEmpty());
  }

  private Credential credential(
      HotpHashAlgorithm algorithm, int digits, long counter, Map<String, String> metadata) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("hotp.algorithm", algorithm.name());
    attributes.put("hotp.digits", Integer.toString(digits));
    attributes.put("hotp.counter", Long.toString(counter));
    metadata.forEach((key, value) -> attributes.put("hotp.metadata." + key, value));
    return Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes);
  }

  private Map<String, String> metadata(String presetKey, String label) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("presetKey", presetKey);
    metadata.put("presetLabel", label);
    metadata.put("notes", "Seeded HOTP credential");
    return metadata;
  }

  private static String generateOtp(HotpHashAlgorithm algorithm, int digits, long counter) {
    HotpDescriptor descriptor = HotpDescriptor.create(CREDENTIAL_ID, SECRET, algorithm, digits);
    return HotpGenerator.generate(descriptor, counter);
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final ConcurrentHashMap<String, Credential> data = new ConcurrentHashMap<>();

    @Override
    public void save(Credential credential) {
      data.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(data.get(name));
    }

    @Override
    public List<Credential> findAll() {
      return new ArrayList<>(data.values());
    }

    @Override
    public boolean delete(String name) {
      return data.remove(name) != null;
    }

    @Override
    public void close() {
      data.clear();
    }
  }
}
