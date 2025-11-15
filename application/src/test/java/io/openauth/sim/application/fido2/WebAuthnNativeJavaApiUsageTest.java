package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnNativeJavaApiUsageTest {

    private static final String RP_ID = "example.com";
    private static final String ORIGIN = "https://example.com";
    private static final String EXPECTED_TYPE = "webauthn.get";

    private CredentialStore credentialStore;
    private WebAuthnCredentialPersistenceAdapter persistenceAdapter;
    private WebAuthnEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
        WebAuthnAssertionVerifier verifier = new WebAuthnAssertionVerifier();
        service = new WebAuthnEvaluationApplicationService(credentialStore, verifier, persistenceAdapter);
    }

    @Test
    void storedAssertionEvaluationEmitsSuccessWithTelemetry() {
        byte[] credentialId = "credential-001".getBytes(StandardCharsets.UTF_8);
        WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                .name("credential-001")
                .relyingPartyId(RP_ID)
                .credentialId(credentialId)
                .publicKeyCose(new byte[] {1, 2, 3})
                .signatureCounter(0L)
                .userVerificationRequired(true)
                .algorithm(WebAuthnSignatureAlgorithm.ES256)
                .build();
        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
        credentialStore.save(credential);

        byte[] clientDataJson =
                "{\"type\":\"webauthn.get\",\"challenge\":\"Y2hhbGxlbmdl\",\"origin\":\"https://example.com\"}"
                        .getBytes(StandardCharsets.UTF_8);
        byte[] authenticatorData = new byte[] {0x01};
        byte[] signature = new byte[] {0x02};
        byte[] expectedChallenge = Base64.getUrlDecoder().decode("Y2hhbGxlbmdl");

        EvaluationCommand.Stored command = new EvaluationCommand.Stored(
                "credential-001",
                RP_ID,
                ORIGIN,
                EXPECTED_TYPE,
                expectedChallenge,
                clientDataJson,
                authenticatorData,
                signature);

        EvaluationResult result = service.evaluate(command);

        assertTrue(result.credentialReference());
        assertEquals("credential-001", result.credentialId());
    }

    @Test
    void inlineAssertionEvaluationFailureSurfacesError() {
        byte[] credentialId = "inline-credential".getBytes(StandardCharsets.UTF_8);
        byte[] publicKeyCose = new byte[] {0x01, 0x02};
        byte[] clientDataJson =
                "{\"type\":\"webauthn.get\",\"challenge\":\"Y2hhbGxlbmdl\",\"origin\":\"https://example.com\"}"
                        .getBytes(StandardCharsets.UTF_8);
        byte[] authenticatorData = new byte[] {0x01};
        byte[] signature = new byte[] {0x02};
        byte[] expectedChallenge = Base64.getUrlDecoder().decode("Y2hhbGxlbmdl");

        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                "inline-demo",
                RP_ID,
                ORIGIN,
                EXPECTED_TYPE,
                credentialId,
                publicKeyCose,
                0L,
                true,
                WebAuthnSignatureAlgorithm.ES256,
                expectedChallenge,
                clientDataJson,
                authenticatorData,
                signature);

        EvaluationResult result = service.evaluate(command);

        assertFalse(result.valid());
        assertFalse(result.credentialReference());
        assertNull(result.credentialId());
        assertEquals(WebAuthnSignatureAlgorithm.ES256, result.algorithm());
        assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
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
