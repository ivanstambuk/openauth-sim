package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayResult;
import io.openauth.sim.application.hotp.HotpReplayApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.TelemetryContractTestSupport;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HotpReplayApplicationServiceTest {

  private static final String CREDENTIAL_ID = "hotp-credential";
  private static final HotpHashAlgorithm ALGORITHM = HotpHashAlgorithm.SHA1;
  private static final int DIGITS = 6;
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("3132333435363738393031323334353637383930");

  private InMemoryCredentialStore store;
  private HotpReplayApplicationService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryCredentialStore();
    service = new HotpReplayApplicationService(store);
  }

  @Test
  void storedReplayReturnsMatchWithoutAdvancingCounter() {
    long counter = 5L;
    Credential credential =
        Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(counter));
    store.save(credential);

    HotpDescriptor descriptor = HotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS);
    String otp = HotpGenerator.generate(descriptor, counter);

    ReplayResult result = service.replay(new ReplayCommand.Stored(CREDENTIAL_ID, otp));

    assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
    assertEquals("match", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertTrue(result.credentialReference());
    assertEquals(counter, result.previousCounter());
    assertEquals(counter, result.nextCounter());

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals(Long.toString(counter), persisted.attributes().get("hotp.counter"));

    TelemetryFrame frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.hotpReplayAdapter(), TelemetryContractTestSupport.telemetryId());
    TelemetryContractTestSupport.assertHotpReplaySuccessFrame(frame, "stored", counter);
  }

  @Test
  void storedReplayMismatchKeepsCounterAndEmitsValidationTelemetry() {
    long counter = 5L;
    Credential credential =
        Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(counter));
    store.save(credential);

    ReplayResult result = service.replay(new ReplayCommand.Stored(CREDENTIAL_ID, "000000"));

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("otp_mismatch", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(counter, result.previousCounter());
    assertEquals(counter, result.nextCounter());

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals(Long.toString(counter), persisted.attributes().get("hotp.counter"));

    TelemetryFrame frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.hotpReplayAdapter(), TelemetryContractTestSupport.telemetryId());
    TelemetryContractTestSupport.assertHotpReplayValidationFrame(frame, true, "stored", counter);
  }

  @Test
  void storedReplayMissingCredentialReturnsNotFoundTelemetry() {
    ReplayResult result = service.replay(new ReplayCommand.Stored(CREDENTIAL_ID, "123456"));

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("credential_not_found", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertTrue(result.credentialReference());
    assertEquals(0L, result.previousCounter());
    assertEquals(0L, result.nextCounter());
  }

  @Test
  void inlineReplayReturnsMatchWithoutPersistingState() {
    long counter = 5L;
    HotpDescriptor descriptor = HotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS);
    String otp = HotpGenerator.generate(descriptor, counter);

    ReplayCommand.Inline command =
        new ReplayCommand.Inline(
            CREDENTIAL_ID,
            SECRET.asHex(),
            ALGORITHM,
            DIGITS,
            counter,
            otp,
            Map.of("source", "test"));

    ReplayResult result = service.replay(command);

    assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
    assertFalse(result.credentialReference());
    assertEquals(counter, result.previousCounter());
    assertEquals(counter, result.nextCounter());
    assertTrue(store.findAll().isEmpty());

    TelemetryFrame frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.hotpReplayAdapter(), TelemetryContractTestSupport.telemetryId());
    TelemetryContractTestSupport.assertHotpReplaySuccessFrame(frame, "inline", counter);
  }

  @Test
  void inlineReplayMismatchEmitsValidationTelemetry() {
    ReplayCommand.Inline command =
        new ReplayCommand.Inline(
            CREDENTIAL_ID, SECRET.asHex(), ALGORITHM, DIGITS, 5L, "000000", Map.of());

    ReplayResult result = service.replay(command);

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("otp_mismatch", result.telemetry().reasonCode());
    assertEquals(5L, result.previousCounter());
    assertEquals(5L, result.nextCounter());

    TelemetryFrame frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.hotpReplayAdapter(), TelemetryContractTestSupport.telemetryId());
    TelemetryContractTestSupport.assertHotpReplayValidationFrame(frame, true, "inline", 5L);
  }

  private static Map<String, String> attributes(long counter) {
    return Map.of(
        "hotp.algorithm", ALGORITHM.name(),
        "hotp.digits", Integer.toString(DIGITS),
        "hotp.counter", Long.toString(counter));
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
