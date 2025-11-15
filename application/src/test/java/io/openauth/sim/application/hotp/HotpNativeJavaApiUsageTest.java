package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures.HotpJsonVector;
import io.openauth.sim.core.store.CredentialStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HotpNativeJavaApiUsageTest {

    private CredentialStore credentialStore;
    private HotpEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        service = new HotpEvaluationApplicationService(credentialStore);
    }

    @Test
    void storedEvaluationMatchesHotpFixtures() {
        HotpJsonVector vector = HotpJsonVectorFixtures.getById("rfc4226_sha1_digits6_counter0");
        credentialStore.save(
                Credential.create("otp-001", CredentialType.OATH_HOTP, vector.secret(), attributes(vector.counter())));

        EvaluationCommand.Stored command = new EvaluationCommand.Stored("otp-001", 0, 0);

        EvaluationResult result = service.evaluate(command);

        assertTrue(result.credentialReference(), "expected stored evaluation to reference a credential");
        assertEquals("otp-001", result.credentialId());
        assertEquals(vector.counter(), result.previousCounter());
        assertEquals(vector.counter() + 1, result.nextCounter());
        assertEquals(vector.algorithm(), result.algorithm());
        assertEquals(Integer.valueOf(vector.digits()), result.digits());
        assertEquals(vector.otp(), result.otp());
        assertNotNull(result.previews());
        assertFalse(result.previews().isEmpty(), "expected at least one preview entry");
    }

    @Test
    void inlineEvaluationUsesSecretAndCounterFromCommand() {
        HotpJsonVector vector = HotpJsonVectorFixtures.getById("rfc4226_sha1_digits6_counter0");

        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                toHex(vector.secret()), vector.algorithm(), vector.digits(), vector.counter(), Map.of(), 0, 0);

        EvaluationResult result = service.evaluate(command);

        assertFalse(result.credentialReference(), "inline evaluation should not reference a stored credential");
        assertNull(result.credentialId());
        assertEquals(vector.counter(), result.previousCounter());
        assertEquals(vector.counter() + 1, result.nextCounter());
        assertEquals(vector.algorithm(), result.algorithm());
        assertEquals(Integer.valueOf(vector.digits()), result.digits());
        assertEquals(vector.otp(), result.otp());
    }

    @Test
    void inlineEvaluationWithMissingCounterYieldsValidationFailure() {
        HotpJsonVector vector = HotpJsonVectorFixtures.getById("rfc4226_sha1_digits6_counter0");

        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                toHex(vector.secret()), vector.algorithm(), vector.digits(), null, Map.of(), 0, 0);

        EvaluationResult result = service.evaluate(command);

        assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
        assertNull(result.otp(), "OTP should not be produced on validation failure");
        assertEquals(0L, result.previousCounter());
        assertEquals(0L, result.nextCounter());
    }

    private static String toHex(SecretMaterial secret) {
        byte[] bytes = secret.value();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
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
