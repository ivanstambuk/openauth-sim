package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void evaluateStoredCredentialAdvancesCounterAndBuildsTelemetry() {
    Credential credential =
        Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(0L));
    store.save(credential);

    HotpDescriptor descriptor = HotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS);
    String otp = HotpGenerator.generate(descriptor, 0L);

    EvaluationResult result = service.evaluate(new EvaluationCommand.Stored(CREDENTIAL_ID, otp));

    assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
    assertEquals("match", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(1L, result.nextCounter());
    assertEquals(0L, result.previousCounter());
    assertTrue(result.credentialReference());

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
  void evaluateMismatchedOtpKeepsCounterAndFlagsInvalidTelemetry() {
    Credential credential =
        Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(0L));
    store.save(credential);

    EvaluationResult result =
        service.evaluate(new EvaluationCommand.Stored(CREDENTIAL_ID, "000000"));

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("otp_mismatch", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(0L, result.nextCounter());
    assertEquals(0L, result.previousCounter());

    Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
    assertEquals("0", persisted.attributes().get("hotp.counter"));

    TelemetryFrame frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.hotpEvaluationAdapter(),
                TelemetryContractTestSupport.telemetryId());
    TelemetryContractTestSupport.assertHotpEvaluationValidationFrame(frame, true);
  }

  @Test
  void evaluateStoredCredentialNotFoundProducesInvalidTelemetry() {
    EvaluationResult result =
        service.evaluate(new EvaluationCommand.Stored(CREDENTIAL_ID, "123456"));

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("credential_not_found", result.telemetry().reasonCode());
    assertTrue(result.telemetry().sanitized());
    assertEquals(0L, result.previousCounter());
    assertEquals(0L, result.nextCounter());
  }

  @Test
  void evaluateInlineCredentialSucceedsWithoutPersistingCounter() {
    EvaluationCommand.Inline command =
        new EvaluationCommand.Inline(
            SECRET.asHex(),
            ALGORITHM,
            DIGITS,
            5L,
            HotpGenerator.generate(
                HotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS), 5L),
            Map.of("source", "test"));

    EvaluationResult result = service.evaluate(command);

    assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
    assertTrue(result.telemetry().sanitized());
    assertEquals(6L, result.nextCounter());
    assertEquals(5L, result.previousCounter());
    assertTrue(!result.credentialReference());
    assertEquals(null, result.credentialId());
    assertTrue(store.findAll().isEmpty());
  }

  @Test
  void evaluateInlineRejectsNonNumericOtp() {
    EvaluationCommand.Inline command =
        new EvaluationCommand.Inline(SECRET.asHex(), ALGORITHM, DIGITS, 5L, "ABCDEF", Map.of());

    EvaluationResult result = service.evaluate(command);

    assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("otp_mismatch", result.telemetry().reasonCode());
    assertEquals(5L, result.previousCounter());
    assertEquals(5L, result.nextCounter());
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
