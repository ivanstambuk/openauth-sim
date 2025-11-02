package io.openauth.sim.application.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayResult;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedCommand;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures.ReplayFixture;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;

final class EmvCapReplayApplicationServiceTest {

    private InMemoryCredentialStore credentialStore;
    private EmvCapEvaluationApplicationService evaluationService;
    private EmvCapReplayApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        evaluationService = new EmvCapEvaluationApplicationService();
        service = new EmvCapReplayApplicationService(credentialStore, evaluationService);

        EmvCapSeedApplicationService seeder = new EmvCapSeedApplicationService();
        List<SeedCommand> commands = new ArrayList<>();
        for (SeedSample sample : EmvCapSeedSamples.samples()) {
            commands.add(sample.toSeedCommand());
        }
        seeder.seed(commands, credentialStore);
    }

    @org.junit.jupiter.api.Test
    void storedReplayMatchesBaselineFixture() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        ReplayCommand.Stored command = new ReplayCommand.Stored(
                fixture.credentialId(),
                fixture.mode(),
                fixture.otpDecimal(),
                fixture.previewWindow().backward(),
                fixture.previewWindow().forward(),
                Optional.empty());

        ReplayResult result = service.replay(command, true);

        assertTrue(result.match(), "Stored replay should match baseline OTP");
        assertEquals(OptionalInt.of(0), result.matchedDelta(), "Matched delta should be zero for baseline");
        assertEquals("stored", result.credentialSource());
        assertEquals(Optional.of(fixture.credentialId()), result.credentialId());
        assertEquals(fixture.previewWindow().backward(), result.driftBackward());
        assertEquals(fixture.previewWindow().forward(), result.driftForward());
        assertEquals(fixture.mode(), result.mode());
        assertTrue(result.traceOptional().isPresent(), "Verbose trace expected when verbose flag is true");
        assertEquals(TelemetryStatus.SUCCESS, result.telemetry().status(), "Telemetry should mark replay as success");
    }

    @org.junit.jupiter.api.Test
    void inlineReplayRejectsMismatchedOtp() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        EvaluationRequest request = new EvaluationRequest(
                vector.input().mode(),
                vector.input().masterKeyHex(),
                vector.input().atcHex(),
                vector.input().branchFactor(),
                vector.input().height(),
                vector.input().ivHex(),
                vector.input().cdol1Hex(),
                vector.input().issuerProprietaryBitmapHex(),
                new CustomerInputs(
                        vector.input().customerInputs().challenge(),
                        vector.input().customerInputs().reference(),
                        vector.input().customerInputs().amount()),
                new TransactionData(
                        vector.input().transactionData().terminalHexOverride(),
                        vector.input().transactionData().iccHexOverride()),
                vector.input().iccDataTemplateHex(),
                vector.input().issuerApplicationDataHex());

        ReplayCommand.Inline command = new ReplayCommand.Inline(
                request,
                fixture.mismatchOtpDecimal(),
                fixture.previewWindow().backward(),
                fixture.previewWindow().forward());

        ReplayResult result = service.replay(command, true);

        assertFalse(result.match(), "Inline replay should reject mismatched OTP");
        assertTrue(result.matchedDelta().isEmpty(), "Matched delta is absent when replay fails");
        assertEquals("inline", result.credentialSource());
        assertTrue(result.credentialId().isEmpty(), "Inline replay should not expose credentialId");
        assertEquals(fixture.previewWindow().backward(), result.driftBackward());
        assertEquals(fixture.previewWindow().forward(), result.driftForward());
        assertEquals(fixture.mode(), result.mode());
        assertEquals(TelemetryStatus.INVALID, result.telemetry().status(), "Telemetry should mark mismatch as invalid");
    }

    private static final class InMemoryCredentialStore implements CredentialStore {

        private final Map<String, Credential> credentials = new ConcurrentHashMap<>();

        @Override
        public void save(Credential credential) {
            credentials.put(credential.name(), credential);
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(credentials.get(name));
        }

        @Override
        public List<Credential> findAll() {
            return List.copyOf(credentials.values());
        }

        @Override
        public boolean delete(String name) {
            return credentials.remove(name) != null;
        }

        @Override
        public void close() {
            credentials.clear();
        }
    }
}
