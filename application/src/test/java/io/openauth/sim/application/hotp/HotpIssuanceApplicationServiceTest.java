package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.IssuanceCommand;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.IssuanceResult;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.TelemetryStatus;
import io.openauth.sim.application.telemetry.TelemetryContractTestSupport;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
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

final class HotpIssuanceApplicationServiceTest {

    private static final String CREDENTIAL_ID = "hotp-credential";
    private static final String SECRET_HEX = "3132333435363738393031323334353637383930";

    private InMemoryCredentialStore store;
    private HotpIssuanceApplicationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryCredentialStore();
        service = new HotpIssuanceApplicationService(store);
    }

    @Test
    void issuesHotpCredentialAndBuildsTelemetry() {
        IssuanceCommand command = new IssuanceCommand(
                CREDENTIAL_ID, SECRET_HEX, HotpHashAlgorithm.SHA1, 6, 0L, Map.of("label", "primary"));

        IssuanceResult result = service.issue(command);

        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
        assertEquals("issued", result.telemetry().reasonCode());
        assertTrue(result.telemetry().sanitized());
        assertTrue(result.created());

        Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
        assertEquals(CredentialType.OATH_HOTP, persisted.type());
        assertEquals(SECRET_HEX, persisted.secret().asHex());
        assertEquals("SHA1", persisted.attributes().get("hotp.algorithm"));
        assertEquals("6", persisted.attributes().get("hotp.digits"));
        assertEquals("0", persisted.attributes().get("hotp.counter"));

        TelemetryFrame frame = result.telemetry()
                .emit(TelemetryContracts.hotpIssuanceAdapter(), TelemetryContractTestSupport.telemetryId());
        TelemetryContractTestSupport.assertHotpIssuanceSuccessFrame(frame);
    }

    @Test
    void rejectsIssuanceWhenCredentialTypeDiffers() {
        Credential existing = Credential.create(
                CREDENTIAL_ID, CredentialType.OATH_OCRA, SecretMaterial.fromHex(SECRET_HEX), Map.of());
        store.save(existing);

        IssuanceResult result =
                service.issue(new IssuanceCommand(CREDENTIAL_ID, SECRET_HEX, HotpHashAlgorithm.SHA1, 6, 0L, Map.of()));

        assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
        assertEquals("type_mismatch", result.telemetry().reasonCode());
        assertTrue(result.telemetry().sanitized());
    }

    @Test
    void rejectsIssuanceWithBlankMetadataKey() {
        IssuanceResult result = service.issue(
                new IssuanceCommand(CREDENTIAL_ID, SECRET_HEX, HotpHashAlgorithm.SHA1, 6, 0L, Map.of("", "value")));

        assertEquals(TelemetryStatus.INVALID, result.telemetry().status());
        assertEquals("validation_error", result.telemetry().reasonCode());
        assertTrue(store.findByName(CREDENTIAL_ID).isEmpty());
    }

    @Test
    void reissuesExistingCredentialUpdatesSecretWithoutMarkingCreated() {
        service.issue(new IssuanceCommand(CREDENTIAL_ID, SECRET_HEX, HotpHashAlgorithm.SHA1, 6, 0L, Map.of()));

        IssuanceResult result = service.issue(new IssuanceCommand(
                CREDENTIAL_ID, "A1A2A3A4A5A6A7A8A9B0A1A2A3A4A5A6A7A8A9B0", HotpHashAlgorithm.SHA1, 6, 10L, Map.of()));

        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status());
        assertTrue(!result.created());
        Credential persisted = store.findByName(CREDENTIAL_ID).orElseThrow();
        assertEquals("10", persisted.attributes().get("hotp.counter"));
        assertEquals(
                "a1a2a3a4a5a6a7a8a9b0a1a2a3a4a5a6a7a8a9b0", persisted.secret().asHex());
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
