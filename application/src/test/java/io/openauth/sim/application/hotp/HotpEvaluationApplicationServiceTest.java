package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.TelemetryContractTestSupport;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HotpEvaluationApplicationServiceTest {

  private static final String CREDENTIAL_ID = "hotp-credential";
  private static final HotpHashAlgorithm ALGORITHM = HotpHashAlgorithm.SHA1;
  private static final int DIGITS = 6;
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("3132333435363738393031323334353637383930");

  private InMemoryCredentialStore store;
  private HotpEvaluationApplicationService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryCredentialStore();
    service = new HotpEvaluationApplicationService(store);
  }

  @Test
  void evaluateStoredCredentialGeneratesOtpAndAdvancesCounter() {
    Credential credential =
        Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(0L));
    store.save(credential);

    EvaluationResult result = service.evaluate(new EvaluationCommand.Stored(CREDENTIAL_ID));

    assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
    assertEquals("generated", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(1L, result.nextCounter());
    assertEquals(0L, result.previousCounter());
    assertTrue(result.credentialReference());
    assertEquals(CREDENTIAL_ID, result.credentialId());
    assertNotNull(result.otp());

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals("1", persisted.attributes().get("hotp.counter"));

    TelemetryFrame frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.hotpEvaluationAdapter(),
                TelemetryContractTestSupport.telemetryId());
    TelemetryContractTestSupport.assertHotpEvaluationSuccessFrame(frame);
  }

  @Test
  void evaluateStoredCredentialNotFoundProducesInvalidTelemetry() {
    EvaluationResult result = service.evaluate(new EvaluationCommand.Stored("missing"));

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("credential_not_found", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(0L, result.previousCounter());
    assertEquals(0L, result.nextCounter());
    assertEquals(null, result.otp());
  }

  @Test
  void evaluateStoredCounterOverflowReturnsValidationFailure() {
    Credential credential =
        Credential.create(
            CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(Long.MAX_VALUE));
    store.save(credential);

    EvaluationResult result = service.evaluate(new EvaluationCommand.Stored(CREDENTIAL_ID));

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("counter_overflow", result.telemetry().reasonCode());
    assertEquals(Long.MAX_VALUE, result.previousCounter());
    assertEquals(Long.MAX_VALUE, result.nextCounter());
    assertEquals(null, result.otp());

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals(Long.toString(Long.MAX_VALUE), persisted.attributes().get("hotp.counter"));
  }

  @Test
  void evaluateInlineCredentialGeneratesOtpWithoutPersisting() {
    EvaluationCommand.Inline command =
        new EvaluationCommand.Inline(
            SECRET.asHex(), ALGORITHM, DIGITS, 5L, Map.of("source", "test"));

    EvaluationResult result = service.evaluate(command);

    assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
    assertEquals("generated", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(6L, result.nextCounter());
    assertEquals(5L, result.previousCounter());
    assertTrue(!result.credentialReference());
    assertEquals(null, result.credentialId());
    assertTrue(store.findAll().isEmpty());
    assertNotNull(result.otp());
  }

  @Test
  void evaluateInlineRequiresCounter() {
    EvaluationCommand.Inline command =
        new EvaluationCommand.Inline(SECRET.asHex(), ALGORITHM, DIGITS, null, Map.of());

    EvaluationResult result = service.evaluate(command);

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("counter_required", result.telemetry().reasonCode());
    assertEquals(0L, result.previousCounter());
    assertEquals(0L, result.nextCounter());
  }

  @Test
  void evaluateInlineRequiresSecret() {
    EvaluationCommand.Inline command =
        new EvaluationCommand.Inline("   ", ALGORITHM, DIGITS, 0L, Map.of());

    EvaluationResult result = service.evaluate(command);

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("sharedSecretHex_required", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(0L, result.previousCounter());
    assertEquals(0L, result.nextCounter());
  }

  @Test
  void evaluateInlineCounterOverflowReturnsValidationFailure() {
    EvaluationCommand.Inline command =
        new EvaluationCommand.Inline(SECRET.asHex(), ALGORITHM, DIGITS, Long.MAX_VALUE, Map.of());

    EvaluationResult result = service.evaluate(command);

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("counter_overflow", result.telemetry().reasonCode());
    assertEquals(Long.MAX_VALUE, result.previousCounter());
    assertEquals(Long.MAX_VALUE, result.nextCounter());
    assertEquals(null, result.otp());
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
