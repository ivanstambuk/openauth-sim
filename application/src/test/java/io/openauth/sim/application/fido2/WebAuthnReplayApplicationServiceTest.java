package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

final class WebAuthnReplayApplicationServiceTest {

  private static final String CREDENTIAL_ID = "fido2-packed-es256";

  private WebAuthnFixture fixture;
  private InMemoryCredentialStore credentialStore;
  private WebAuthnCredentialPersistenceAdapter persistenceAdapter;
  private WebAuthnEvaluationApplicationService evaluationService;
  private WebAuthnReplayApplicationService replayService;

  @BeforeEach
  void setUp() {
    fixture = WebAuthnFixtures.loadPackedEs256();
    credentialStore = new InMemoryCredentialStore();
    persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
    evaluationService =
        new WebAuthnEvaluationApplicationService(
            credentialStore, new WebAuthnAssertionVerifier(), persistenceAdapter);
    replayService = new WebAuthnReplayApplicationService(evaluationService);
  }

  @Test
  void replayStoredDelegatesToEvaluation() {
    saveFixtureCredential();

    WebAuthnReplayApplicationService.ReplayCommand.Stored command =
        new WebAuthnReplayApplicationService.ReplayCommand.Stored(
            CREDENTIAL_ID,
            fixture.request().relyingPartyId(),
            fixture.request().origin(),
            fixture.request().expectedType(),
            fixture.request().expectedChallenge(),
            fixture.request().clientDataJson(),
            fixture.request().authenticatorData(),
            fixture.request().signature());

    WebAuthnReplayApplicationService.ReplayResult result = replayService.replay(command);

    assertTrue(result.match());
    assertTrue(result.credentialReference());
    assertEquals(CREDENTIAL_ID, result.credentialId());
    assertEquals("stored", result.credentialSource());
    assertTrue(result.error().isEmpty());

    var telemetry = result.telemetry();
    assertEquals(WebAuthnEvaluationApplicationService.TelemetryStatus.SUCCESS, telemetry.status());
    assertEquals("match", telemetry.reasonCode());
    Map<String, Object> fields = telemetry.fields();
    assertEquals("stored", fields.get("credentialSource"));
    assertEquals("example.org", fields.get("relyingPartyId"));

    TelemetryFrame frame =
        result.replayFrame(new Fido2TelemetryAdapter("fido2.replay"), "telemetry-3");
    assertEquals("fido2.replay", frame.event());
    assertEquals("success", frame.status());
  }

  @Test
  void replayInlinePropagatesSignatureFailure() {
    byte[] tamperedSignature = fixture.request().signature().clone();
    tamperedSignature[0] ^= 0x7F;

    WebAuthnReplayApplicationService.ReplayCommand.Inline command =
        new WebAuthnReplayApplicationService.ReplayCommand.Inline(
            "inline-fixture",
            fixture.request().relyingPartyId(),
            fixture.request().origin(),
            fixture.request().expectedType(),
            fixture.storedCredential().credentialId(),
            fixture.storedCredential().publicKeyCose(),
            fixture.storedCredential().signatureCounter(),
            fixture.storedCredential().userVerificationRequired(),
            WebAuthnSignatureAlgorithm.ES256,
            fixture.request().expectedChallenge(),
            fixture.request().clientDataJson(),
            fixture.request().authenticatorData(),
            tamperedSignature);

    WebAuthnReplayApplicationService.ReplayResult result = replayService.replay(command);

    assertFalse(result.match());
    assertFalse(result.credentialReference());
    assertEquals("inline", result.credentialSource());
    assertEquals(Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID), result.error());
    assertEquals(
        WebAuthnEvaluationApplicationService.TelemetryStatus.INVALID, result.telemetry().status());
    assertEquals("signature_invalid", result.telemetry().reasonCode());

    Map<String, Object> fields = result.telemetry().fields();
    assertEquals("inline", fields.get("credentialSource"));
    assertFalse(fields.containsKey("credentialId"));
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
