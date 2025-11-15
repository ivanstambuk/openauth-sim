package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures.TotpJsonVector;
import io.openauth.sim.core.store.CredentialStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TotpNativeJavaApiUsageTest {

    private CredentialStore credentialStore;
    private TotpEvaluationApplicationService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        clock = Clock.fixed(Instant.ofEpochSecond(59), Clock.systemUTC().getZone());
        service = new TotpEvaluationApplicationService(credentialStore, clock);
    }

    @Test
    void storedGenerationMatchesTotpFixtures() {
        TotpJsonVector vector = TotpJsonVectorFixtures.getById("rfc6238_sha1_digits8_t59");
        TotpDescriptor descriptor = TotpDescriptor.create(
                "totp-001",
                vector.secret(),
                vector.algorithm(),
                vector.digits(),
                Duration.ofSeconds(vector.stepSeconds()),
                TotpDriftWindow.of(0, 0));

        credentialStore.save(descriptorToCredential(descriptor));

        EvaluationCommand.Stored command = new EvaluationCommand.Stored(
                "totp-001", "", descriptor.driftWindow(), clock.instant(), Optional.empty());

        EvaluationResult result = service.evaluate(command);

        assertTrue(result.credentialReference(), "expected stored evaluation to reference a credential");
        assertEquals("totp-001", result.credentialId());
        assertEquals(vector.algorithm(), result.algorithm());
        assertEquals(Integer.valueOf(vector.digits()), result.digits());
        assertEquals(Duration.ofSeconds(vector.stepSeconds()), result.stepDuration());
        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
        assertEquals("generated", result.telemetry().reasonCode());
        assertEquals(vector.otp(), result.otp());

        List<OtpPreview> previews = result.previews();
        assertNotNull(previews);
        assertFalse(previews.isEmpty(), "expected at least one preview entry");
    }

    @Test
    void inlineValidationSuccessUsesCallerOtp() {
        TotpJsonVector vector = TotpJsonVectorFixtures.getById("rfc6238_sha1_digits8_t59");

        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                toHex(vector.secret()),
                vector.algorithm(),
                vector.digits(),
                Duration.ofSeconds(vector.stepSeconds()),
                vector.otp(),
                TotpDriftWindow.of(0, 0),
                clock.instant(),
                Optional.empty());

        EvaluationResult result = service.evaluate(command);

        assertFalse(result.credentialReference(), "inline evaluation should not reference a stored credential");
        assertNull(result.credentialId());
        assertEquals(vector.algorithm(), result.algorithm());
        assertEquals(Integer.valueOf(vector.digits()), result.digits());
        assertEquals(Duration.ofSeconds(vector.stepSeconds()), result.stepDuration());
        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
        assertEquals("validated", result.telemetry().reasonCode());
        assertNull(result.otp(), "OTP field is null on validation flows; see previews for context.");
    }

    @Test
    void inlineValidationFailureOutOfWindow() {
        TotpJsonVector vector = TotpJsonVectorFixtures.getById("rfc6238_sha1_digits8_t59");

        Instant lateInstant = clock.instant().plusSeconds(600);

        EvaluationCommand.Inline command = new EvaluationCommand.Inline(
                toHex(vector.secret()),
                vector.algorithm(),
                vector.digits(),
                Duration.ofSeconds(vector.stepSeconds()),
                vector.otp(),
                TotpDriftWindow.of(0, 0),
                lateInstant,
                Optional.empty());

        EvaluationResult result = service.evaluate(command);

        assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
        assertEquals("otp_out_of_window", result.telemetry().reasonCode());
        assertNull(result.otp(), "OTP should not be surfaced on validation failure");
        assertTrue(result.previews().isEmpty(), "previews should be empty on failure");
    }

    private static String toHex(SecretMaterial secret) {
        byte[] bytes = secret.value();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static Credential descriptorToCredential(TotpDescriptor descriptor) {
        Map<String, String> attributes =
                new TotpCredentialPersistenceAdapter().serialize(descriptor).attributes();
        return Credential.create(descriptor.name(), CredentialType.OATH_TOTP, descriptor.secret(), attributes);
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
