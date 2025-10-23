package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class HotpEvaluationApplicationServiceVerboseTraceTest {

    private CredentialStore credentialStore;
    private HotpEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        service = new HotpEvaluationApplicationService(credentialStore);
    }

    @Test
    void storedEvaluationWithVerboseCapturesTrace() {
        var secret = SecretMaterial.fromHex("3132333435363738393031323334353637383930");
        credentialStore.save(Credential.create("otp-001", CredentialType.OATH_HOTP, secret, attributes(7L)));

        var command = new HotpEvaluationApplicationService.EvaluationCommand.Stored("otp-001");

        var verboseResult = service.evaluate(command, true);
        assertTrue(verboseResult.verboseTrace().isPresent(), "expected verbose trace when enabled");
        var trace = verboseResult.verboseTrace().orElseThrow();

        assertEquals("hotp.evaluate.stored", trace.operation());
        assertEquals("HOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals("otp-001", trace.metadata().get("credentialId"));

        assertFalse(trace.steps().isEmpty(), "trace should contain intermediate steps");

        var firstStep = trace.steps().get(0);
        assertEquals("resolve.credential", firstStep.id());
        assertEquals(true, firstStep.attributes().get("found"));
        assertEquals(7L, firstStep.attributes().get("counter.before"));

        var generateStep = trace.steps().stream()
                .filter(step -> "generate.otp".equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("generate.otp step missing"));
        assertEquals("HotpGenerator.generate", generateStep.detail());
        assertEquals(6, generateStep.attributes().get("digits"));
        assertEquals(HotpHashAlgorithm.SHA1.name(), generateStep.attributes().get("algorithm"));
        assertEquals(7L, generateStep.attributes().get("counter.input"));

        var persistStep = trace.steps().stream()
                .filter(step -> "persist.counter".equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("persist.counter step missing"));
        assertEquals(8L, persistStep.attributes().get("counter.next"));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        var command = new HotpEvaluationApplicationService.EvaluationCommand.Inline(
                "3132333435363738393031323334353637383930", HotpHashAlgorithm.SHA1, 6, 1L, Map.of());

        var result = service.evaluate(command, false);
        assertTrue(result.verboseTrace().isEmpty(), "trace should be absent when verbose flag is disabled");
    }

    private static Map<String, String> attributes(long counter) {
        return Map.of(
                "hotp.algorithm", HotpHashAlgorithm.SHA1.name(),
                "hotp.digits", Integer.toString(6),
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
