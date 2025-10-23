package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnEvaluationApplicationServiceVerboseTraceTest {

    private static final String CREDENTIAL_ID = "fido2-trace-credential";

    private WebAuthnFixture fixture;
    private InMemoryCredentialStore credentialStore;
    private WebAuthnCredentialPersistenceAdapter persistenceAdapter;
    private WebAuthnEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        fixture = WebAuthnFixtures.loadPackedEs256();
        credentialStore = new InMemoryCredentialStore();
        persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
        service = new WebAuthnEvaluationApplicationService(
                credentialStore, new WebAuthnAssertionVerifier(), persistenceAdapter);
    }

    @Test
    void storedEvaluationWithVerboseCapturesTrace() {
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

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("fido2.assertion.evaluate.stored", trace.operation());
        assertEquals("FIDO2", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals(CREDENTIAL_ID, trace.metadata().get("credentialName"));

        assertTrue(trace.steps().stream().anyMatch(step -> "resolve.credential".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "deserialize.descriptor".equals(step.id())));
        assertTrue(trace.steps().stream()
                .anyMatch(step -> "verify.assertion".equals(step.id())
                        && Boolean.TRUE.equals(step.attributes().get("valid"))));
    }

    @Test
    void inlineEvaluationWithVerboseCapturesTrace() {
        WebAuthnEvaluationApplicationService.EvaluationCommand.Inline command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Inline(
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
                        fixture.request().signature());

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("fido2.assertion.evaluate.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));
        assertTrue(trace.steps().stream().anyMatch(step -> "construct.credential".equals(step.id())));
        assertTrue(trace.steps().stream().anyMatch(step -> "verify.assertion".equals(step.id())));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
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

        assertTrue(result.verboseTrace().isEmpty());
    }

    private void saveFixtureCredential() {
        WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                .name(CREDENTIAL_ID)
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(WebAuthnSignatureAlgorithm.ES256)
                .build();

        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
        credentialStore.save(credential);
    }

    private static final class InMemoryCredentialStore implements CredentialStore {
        private final Map<String, Credential> data = new ConcurrentHashMap<>();

        @Override
        public void save(Credential credential) {
            data.put(credential.name(), credential);
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(data.get(name));
        }

        @Override
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(data.values());
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
