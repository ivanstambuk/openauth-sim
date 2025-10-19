package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class WebAuthnJsonVectorEvaluationApplicationServiceTest {

    private static Stream<WebAuthnJsonVector> vectors() {
        return WebAuthnJsonVectorFixtures.loadAll();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void storedEvaluationValidatesJsonVectors(WebAuthnJsonVector vector) {
        WebAuthnCredentialPersistenceAdapter persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
        InMemoryCredentialStore credentialStore = new InMemoryCredentialStore();
        WebAuthnEvaluationApplicationService service = new WebAuthnEvaluationApplicationService(
                credentialStore, new WebAuthnAssertionVerifier(), persistenceAdapter);

        save(vector, persistenceAdapter, credentialStore);

        WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                        vector.vectorId(),
                        vector.assertionRequest().relyingPartyId(),
                        vector.assertionRequest().origin(),
                        vector.assertionRequest().expectedType(),
                        vector.assertionRequest().expectedChallenge(),
                        vector.assertionRequest().clientDataJson(),
                        vector.assertionRequest().authenticatorData(),
                        vector.assertionRequest().signature());

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

        assertTrue(result.valid(), "Expected JSON vector %s to evaluate successfully".formatted(vector));
        assertTrue(result.credentialReference());
        assertEquals(vector.vectorId(), result.credentialId());
        assertEquals(vector.assertionRequest().relyingPartyId(), result.relyingPartyId());
        assertEquals(vector.algorithm(), result.algorithm());
        assertTrue(result.error().isEmpty());
    }

    private static void save(
            WebAuthnJsonVector vector,
            WebAuthnCredentialPersistenceAdapter persistenceAdapter,
            InMemoryCredentialStore credentialStore) {
        WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                .name(vector.vectorId())
                .relyingPartyId(vector.storedCredential().relyingPartyId())
                .credentialId(vector.storedCredential().credentialId())
                .publicKeyCose(vector.storedCredential().publicKeyCose())
                .signatureCounter(vector.storedCredential().signatureCounter())
                .userVerificationRequired(vector.storedCredential().userVerificationRequired())
                .algorithm(vector.algorithm())
                .build();

        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
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
