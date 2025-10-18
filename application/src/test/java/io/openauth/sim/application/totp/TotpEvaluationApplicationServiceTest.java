package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.telemetry.TelemetryContractTestSupport;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TotpEvaluationApplicationServiceTest {

  private static final String CREDENTIAL_ID = "totp-credential";
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("31323334353637383930313233343536");
  private static final Duration STEP = Duration.ofSeconds(30);
  private static final TotpHashAlgorithm ALGORITHM = TotpHashAlgorithm.SHA1;

  private InMemoryCredentialStore credentialStore;
  private Clock clock;
  private TotpEvaluationApplicationService service;

  @BeforeEach
  void setUp() {
    credentialStore = new InMemoryCredentialStore();
    clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
    service = new TotpEvaluationApplicationService(credentialStore, clock);
  }

  @Test
  void storedEvaluationGeneratesOtpWithinConfiguredDriftWindow() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, 6, STEP, TotpDriftWindow.of(1, 1));
    TotpCredentialPersistenceAdapter adapter =
        new TotpCredentialPersistenceAdapter(
            Clock.fixed(Instant.parse("2025-10-08T00:00:00Z"), ZoneOffset.UTC));
    VersionedCredentialRecord record = adapter.serialize(descriptor);
    Credential credential = VersionedCredentialRecordMapper.toCredential(record);
    credentialStore.save(credential);

    Instant evaluationInstant = Instant.ofEpochSecond(1_111_111_111L);
    String expectedOtp = TotpGenerator.generate(descriptor, evaluationInstant);

    TotpEvaluationApplicationService.EvaluationCommand.Stored command =
        new TotpEvaluationApplicationService.EvaluationCommand.Stored(
            CREDENTIAL_ID, "", TotpDriftWindow.of(1, 1), evaluationInstant, Optional.empty());

    TotpEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

    assertEquals(
        TotpEvaluationApplicationService.TelemetryStatus.SUCCESS,
        result.telemetry().status(),
        "telemetry status");
    assertEquals("generated", result.telemetry().reasonCode());
    assertTrue(result.valid(), "generated OTP should be considered valid output");
    assertTrue(result.credentialReference());
    assertEquals(CREDENTIAL_ID, result.credentialId());
    assertEquals(0, result.matchedSkewSteps(), "generation should report zero skew");
    assertEquals(ALGORITHM, result.algorithm());
    assertEquals(6, result.digits());
    assertEquals(STEP, result.stepDuration());
    assertEquals(TotpDriftWindow.of(1, 1), result.driftWindow());

    var frame =
        result
            .telemetry()
            .emit(
                TelemetryContracts.totpEvaluationAdapter(),
                TelemetryContractTestSupport.telemetryId());
    assertEquals(CREDENTIAL_ID, frame.fields().get("credentialId"));
    assertEquals(0, frame.fields().get("matchedSkewSteps"));
    assertFalse(
        frame.fields().containsKey("otp"),
        "telemetry must not leak generated OTP values; they belong in the response only");
    assertEquals(
        expectedOtp.length(),
        result.digits(),
        "digits metadata should align with generated OTP length");
  }

  @Test
  void inlineEvaluationGeneratesOtpWhenOtpNotSupplied() {
    Instant evaluationInstant = Instant.ofEpochSecond(1_234_567_890L);
    SecretMaterial inlineSecret =
        SecretMaterial.fromStringUtf8(
            "1234567890123456789012345678901234567890123456789012345678901234");

    TotpEvaluationApplicationService.EvaluationCommand.Inline command =
        new TotpEvaluationApplicationService.EvaluationCommand.Inline(
            inlineSecret.asHex(),
            TotpHashAlgorithm.SHA512,
            8,
            Duration.ofSeconds(60),
            "",
            TotpDriftWindow.of(1, 1),
            evaluationInstant,
            Optional.empty());

    TotpEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

    assertEquals(
        TotpEvaluationApplicationService.TelemetryStatus.SUCCESS, result.telemetry().status());
    assertEquals("generated", result.telemetry().reasonCode());
    assertFalse(result.credentialReference());
    assertTrue(result.valid());
  }

  @Test
  void inlineEvaluationRejectsOtpOutsideDriftWindow() {
    Instant issuedAt = Instant.ofEpochSecond(1_234_567_890L);
    SecretMaterial inlineSecret =
        SecretMaterial.fromStringUtf8(
            "1234567890123456789012345678901234567890123456789012345678901234");
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "inline",
            inlineSecret,
            TotpHashAlgorithm.SHA512,
            8,
            Duration.ofSeconds(60),
            TotpDriftWindow.of(0, 0));
    String otp = TotpGenerator.generate(descriptor, issuedAt);

    TotpEvaluationApplicationService.EvaluationCommand.Inline command =
        new TotpEvaluationApplicationService.EvaluationCommand.Inline(
            inlineSecret.asHex(),
            TotpHashAlgorithm.SHA512,
            8,
            Duration.ofSeconds(60),
            otp,
            TotpDriftWindow.of(0, 0),
            Instant.ofEpochSecond(issuedAt.getEpochSecond() + 180),
            Optional.of(issuedAt.minusSeconds(120)));

    TotpEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

    assertEquals(
        TotpEvaluationApplicationService.TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("otp_out_of_window", result.telemetry().reasonCode());
    assertFalse(result.valid());
    assertFalse(result.credentialReference());
    assertEquals(Integer.MIN_VALUE, result.matchedSkewSteps());
  }

  private static final class InMemoryCredentialStore implements CredentialStore {
    private final Map<String, Credential> store = new ConcurrentHashMap<>();
    private final List<Credential> history = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(Credential credential) {
      store.put(credential.name(), credential);
      history.add(credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(store.get(name));
    }

    @Override
    public List<Credential> findAll() {
      return new ArrayList<>(store.values());
    }

    @Override
    public boolean delete(String name) {
      return store.remove(name) != null;
    }

    @Override
    public void close() {
      store.clear();
      history.clear();
    }
  }
}
