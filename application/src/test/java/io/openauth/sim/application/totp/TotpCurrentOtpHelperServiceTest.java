package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.totp.TotpCurrentOtpHelperService.LookupCommand;
import io.openauth.sim.application.totp.TotpCurrentOtpHelperService.LookupResult;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TotpCurrentOtpHelperServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET_HEX = "3132333435363738393031323334353637383930";

    private MapDbCredentialStore store;
    private TotpEvaluationApplicationService evaluationService;

    @BeforeEach
    void setUp() {
        store = MapDbCredentialStore.inMemory().open();
        evaluationService = new TotpEvaluationApplicationService(store, FIXED_CLOCK);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void returnsCurrentOtpForStoredCredential() {
        persistCredential("helper-demo", TotpHashAlgorithm.SHA1, 8, Duration.ofSeconds(30));
        TotpCurrentOtpHelperService service = new TotpCurrentOtpHelperService(evaluationService, FIXED_CLOCK);
        Instant timestamp = Instant.ofEpochSecond(59);
        LookupCommand command =
                new LookupCommand("helper-demo", TotpDriftWindow.of(1, 1), Optional.of(timestamp), Optional.empty());

        LookupResult result = service.lookup(command);

        assertNotNull(result);
        assertEquals("helper-demo", result.evaluationResult().credentialId());
        assertEquals("94287082", result.evaluationResult().otp());
        assertEquals(timestamp, result.generationInstant());
        assertEquals(Duration.ofSeconds(30), result.evaluationResult().stepDuration());
        assertTrue(!result.timestampOverrideProvided());
    }

    @Test
    void returnsInvalidTelemetryWhenCredentialMissing() {
        TotpCurrentOtpHelperService service = new TotpCurrentOtpHelperService(evaluationService, FIXED_CLOCK);
        LookupCommand command =
                new LookupCommand("missing", TotpDriftWindow.of(1, 1), Optional.empty(), Optional.empty());

        LookupResult result = service.lookup(command);

        assertEquals(
                TotpEvaluationApplicationService.TelemetryStatus.INVALID,
                result.evaluationResult().telemetry().status());
        assertEquals(
                "credential_not_found", result.evaluationResult().telemetry().reasonCode());
        assertTrue(result.evaluationResult().otp() == null
                || result.evaluationResult().otp().isBlank());
        assertTrue(!result.timestampOverrideProvided());
    }

    private void persistCredential(String credentialId, TotpHashAlgorithm algorithm, int digits, Duration step) {
        TotpDescriptor descriptor = TotpDescriptor.create(
                credentialId, SecretMaterial.fromHex(SECRET_HEX), algorithm, digits, step, TotpDriftWindow.of(1, 1));
        TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter(FIXED_CLOCK);
        VersionedCredentialRecord record = adapter.serialize(descriptor);
        Credential credential = VersionedCredentialRecordMapper.toCredential(record);
        store.save(credential);
    }
}
