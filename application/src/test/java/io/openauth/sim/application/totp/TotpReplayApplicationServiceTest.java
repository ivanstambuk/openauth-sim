package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.telemetry.TelemetryContractTestSupport;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.totp.TotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.totp.TotpReplayApplicationService.ReplayResult;
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

final class TotpReplayApplicationServiceTest {

    private static final String CREDENTIAL_ID = "totp-replay-credential";
    private static final SecretMaterial SECRET = SecretMaterial.fromHex("31323334353637383930313233343536");
    private static final Duration STEP = Duration.ofSeconds(30);
    private static final TotpHashAlgorithm ALGORITHM = TotpHashAlgorithm.SHA1;

    private InMemoryCredentialStore credentialStore;
    private Clock clock;
    private TotpReplayApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        clock = Clock.fixed(Instant.ofEpochSecond(111_111_110L), ZoneOffset.UTC);
        TotpEvaluationApplicationService evaluationService =
                new TotpEvaluationApplicationService(credentialStore, clock);
        service = new TotpReplayApplicationService(evaluationService);
    }

    @Test
    void storedReplayMatchesWithinConfiguredDriftWindow() {
        TotpDescriptor descriptor =
                TotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, 6, STEP, TotpDriftWindow.of(1, 1));
        TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter(
                Clock.fixed(Instant.parse("2025-10-08T12:30:00Z"), ZoneOffset.UTC));
        VersionedCredentialRecord record = adapter.serialize(descriptor);
        Credential credential = VersionedCredentialRecordMapper.toCredential(record);
        credentialStore.save(credential);

        Instant evaluationInstant = Instant.ofEpochSecond(1_699_999_970L);
        String otp = TotpGenerator.generate(descriptor, evaluationInstant);

        ReplayResult result = service.replay(new ReplayCommand.Stored(
                CREDENTIAL_ID, otp, TotpDriftWindow.of(1, 1), evaluationInstant, Optional.empty()));

        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
        assertTrue(result.match());
        assertTrue(result.credentialReference());
        assertEquals(CREDENTIAL_ID, result.credentialId());
        assertEquals(ALGORITHM, result.algorithm());
        assertEquals(6, result.digits());
        assertEquals(STEP, result.stepDuration());
        assertEquals(TotpDriftWindow.of(1, 1), result.driftWindow());
        assertEquals(0, result.matchedSkewSteps());

        var frame = result.telemetry()
                .emit(TelemetryContracts.totpReplayAdapter(), TelemetryContractTestSupport.telemetryId());
        TelemetryContractTestSupport.assertTotpReplaySuccessFrame(frame, "stored", 0);
    }

    @Test
    void inlineReplayRejectsOtpOutsideDriftWindow() {
        SecretMaterial inlineSecret =
                SecretMaterial.fromHex("3132333435363738393031323334353637383930313233343536373839303132");
        TotpDescriptor descriptor = TotpDescriptor.create(
                "inline", inlineSecret, TotpHashAlgorithm.SHA512, 8, Duration.ofSeconds(60), TotpDriftWindow.of(0, 0));
        Instant issuedAt = Instant.ofEpochSecond(1_700_000_120L);
        String otp = TotpGenerator.generate(descriptor, issuedAt);

        ReplayResult result = service.replay(new ReplayCommand.Inline(
                inlineSecret.asHex(),
                TotpHashAlgorithm.SHA512,
                8,
                Duration.ofSeconds(60),
                otp,
                TotpDriftWindow.of(0, 0),
                Instant.ofEpochSecond(issuedAt.getEpochSecond() + 180),
                Optional.of(issuedAt.minusSeconds(120))));

        assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
        assertFalse(result.match());
        assertFalse(result.credentialReference());
        assertEquals(Integer.MIN_VALUE, result.matchedSkewSteps());

        var frame = result.telemetry()
                .emit(TelemetryContracts.totpReplayAdapter(), TelemetryContractTestSupport.telemetryId());
        TelemetryContractTestSupport.assertTotpReplayValidationFrame(frame, "inline");
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
