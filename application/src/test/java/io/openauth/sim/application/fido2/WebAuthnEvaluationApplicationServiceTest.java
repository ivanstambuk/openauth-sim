package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnEvaluationApplicationServiceTest {

  private static final String CREDENTIAL_ID = "fido2-packed-es256";

  private WebAuthnFixture fixture;
  private InMemoryCredentialStore credentialStore;
  private WebAuthnCredentialPersistenceAdapter persistenceAdapter;
  private WebAuthnEvaluationApplicationService service;

  @BeforeEach
  void setUp() {
    fixture = WebAuthnFixtures.loadPackedEs256();
    credentialStore = new InMemoryCredentialStore();
    persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
    service =
        new WebAuthnEvaluationApplicationService(
            credentialStore, new WebAuthnAssertionVerifier(), persistenceAdapter);
  }

  @Test
  void storedEvaluationSucceedsAndEmitsTelemetry() {
    saveFixtureCredential();

    WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
        new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
            CREDENTIAL_ID,
            fixture.request().relyingPartyId(),
            fixture.request().origin(),
            fixture.request().expectedType(),
            fixture.request().expectedChallenge(),
            fixture.request().clientDataJson(),
            fixture.request().authenticatorData(),
            fixture.request().signature());

    WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

    assertTrue(result.valid());
    assertTrue(result.credentialReference());
    assertEquals(CREDENTIAL_ID, result.credentialId());
    assertEquals("example.org", result.relyingPartyId());
    assertEquals(WebAuthnSignatureAlgorithm.ES256, result.algorithm());
    assertFalse(result.userVerificationRequired());
    assertTrue(result.error().isEmpty());

    WebAuthnEvaluationApplicationService.TelemetrySignal telemetry = result.telemetry();
    assertEquals(WebAuthnEvaluationApplicationService.TelemetryStatus.SUCCESS, telemetry.status());
    assertEquals("match", telemetry.reasonCode());
    Map<String, Object> fields = telemetry.fields();
    assertEquals("stored", fields.get("credentialSource"));
    assertEquals(true, fields.get("credentialReference"));
    assertEquals(CREDENTIAL_ID, fields.get("credentialId"));
    assertEquals("example.org", fields.get("relyingPartyId"));
    assertEquals("https://example.org", fields.get("origin"));
    assertEquals("ES256", fields.get("algorithm"));
    assertEquals(false, fields.get("userVerificationRequired"));
    assertFalse(fields.containsKey("challenge"));
    assertFalse(fields.containsKey("clientData"));
    assertFalse(fields.containsKey("signature"));

    TelemetryFrame frame =
        telemetry.emit(new Fido2TelemetryAdapter("fido2.evaluate"), "telemetry-1");
    assertEquals("fido2.evaluate", frame.event());
    assertEquals("success", frame.status());
    assertTrue(frame.sanitized());
    assertEquals("telemetry-1", frame.fields().get("telemetryId"));
  }

  @Test
  void storedEvaluationReturnsCredentialNotFound() {
    WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
        new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
            "missing",
            fixture.request().relyingPartyId(),
            fixture.request().origin(),
            fixture.request().expectedType(),
            fixture.request().expectedChallenge(),
            fixture.request().clientDataJson(),
            fixture.request().authenticatorData(),
            fixture.request().signature());

    WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

    assertFalse(result.valid());
    assertFalse(result.credentialReference());
    assertEquals(
        WebAuthnEvaluationApplicationService.TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("credential_not_found", result.telemetry().reasonCode());
    assertTrue(result.error().isEmpty());

    Map<String, Object> fields = result.telemetry().fields();
    assertEquals("stored", fields.get("credentialSource"));
    assertEquals(false, fields.get("credentialReference"));
    assertNull(result.credentialId());
    TelemetryFrame frame =
        result.telemetry().emit(new Fido2TelemetryAdapter("fido2.evaluate"), "telemetry-2");
    assertEquals("invalid", frame.status());
  }

  @Test
  void inlineEvaluationRejectsOriginMismatch() {
    WebAuthnEvaluationApplicationService.EvaluationCommand.Inline command =
        new WebAuthnEvaluationApplicationService.EvaluationCommand.Inline(
            "inline-fixture",
            fixture.request().relyingPartyId(),
            "https://malicious.example.org",
            fixture.request().expectedType(),
            fixture.storedCredential().credentialId(),
            fixture.storedCredential().publicKeyCose(),
            fixture.storedCredential().signatureCounter(),
            fixture.storedCredential().userVerificationRequired(),
            WebAuthnSignatureAlgorithm.ES256,
            fixture.request().expectedChallenge(),
            fixture.request().clientDataJson(),
            fixture.request().authenticatorData(),
            fixture.request().signature());

    WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

    assertFalse(result.valid());
    assertFalse(result.credentialReference());
    assertEquals(Optional.of(WebAuthnVerificationError.ORIGIN_MISMATCH), result.error());
    assertEquals(
        WebAuthnEvaluationApplicationService.TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("origin_mismatch", result.telemetry().reasonCode());

    Map<String, Object> fields = result.telemetry().fields();
    assertEquals("inline", fields.get("credentialSource"));
    assertEquals(false, fields.get("credentialReference"));
    assertFalse(fields.containsKey("credentialId"));
    assertEquals("https://malicious.example.org", fields.get("origin"));
  }

  private void saveFixtureCredential() {
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name(CREDENTIAL_ID)
            .relyingPartyId(fixture.storedCredential().relyingPartyId())
            .credentialId(fixture.storedCredential().credentialId())
            .publicKeyCose(fixture.storedCredential().publicKeyCose())
            .signatureCounter(fixture.storedCredential().signatureCounter())
            .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
            .algorithm(WebAuthnSignatureAlgorithm.ES256)
            .build();

    Credential credential =
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
    credentialStore.save(credential);
  }

  private static final class InMemoryCredentialStore implements CredentialStore {
    private final Map<String, Credential> backing = new ConcurrentHashMap<>();

    @Override
    public void save(Credential credential) {
      backing.put(credential.name(), credential);
    }

    @Override
    public Optional<Credential> findByName(String name) {
      return Optional.ofNullable(backing.get(name));
    }

    @Override
    public java.util.List<Credential> findAll() {
      return java.util.List.copyOf(backing.values());
    }

    @Override
    public boolean delete(String name) {
      return backing.remove(name) != null;
    }

    @Override
    public void close() {
      backing.clear();
    }
  }
}
