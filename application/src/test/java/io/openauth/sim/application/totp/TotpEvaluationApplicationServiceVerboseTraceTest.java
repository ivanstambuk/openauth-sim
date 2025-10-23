package io.openauth.sim.application.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
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

final class TotpEvaluationApplicationServiceVerboseTraceTest {

    private static final String CREDENTIAL_ID = "totp-trace";
    private static final Duration STEP = Duration.ofSeconds(30);
    private static final TotpDriftWindow DRIFT = TotpDriftWindow.of(1, 1);
    private static final SecretMaterial SECRET = SecretMaterial.fromHex("3132333435363738393031323334353637383930");

    private Clock clock;
    private InMemoryCredentialStore store;
    private TotpCredentialPersistenceAdapter persistenceAdapter;
    private TotpEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2025-10-22T12:00:00Z"), ZoneOffset.UTC);
        store = new InMemoryCredentialStore();
        persistenceAdapter = new TotpCredentialPersistenceAdapter(clock);
        service = new TotpEvaluationApplicationService(store, clock);
    }

    @Test
    void storedGenerationWithVerboseCapturesTrace() {
        saveStoredCredential();

        TotpEvaluationApplicationService.EvaluationCommand.Stored command =
                new TotpEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID, "", DRIFT, clock.instant(), Optional.empty());

        TotpEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent(), "expected verbose trace");
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("totp.evaluate.stored", trace.operation());
        assertEquals("TOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals(CREDENTIAL_ID, trace.metadata().get("credentialId"));

        assertEquals(2, trace.steps().size());
        var resolve = trace.steps().get(0);
        assertEquals("resolve.credential", resolve.id());
        assertEquals("CredentialStore.findByName", resolve.detail());
        assertEquals(CREDENTIAL_ID, resolve.attributes().get("credentialId"));
        assertEquals(TotpHashAlgorithm.SHA1.name(), resolve.attributes().get("algorithm"));
        assertEquals(6, resolve.attributes().get("digits"));
        assertEquals(STEP.getSeconds(), resolve.attributes().get("stepSeconds"));
        assertEquals(DRIFT.backwardSteps(), resolve.attributes().get("drift.backward"));
        assertEquals(DRIFT.forwardSteps(), resolve.attributes().get("drift.forward"));

        var generate = trace.steps().get(1);
        assertEquals("generate.otp", generate.id());
        assertEquals("TotpGenerator.generate", generate.detail());
        assertEquals(clock.instant(), generate.attributes().get("evaluationInstant"));
        assertEquals(clock.instant(), generate.attributes().get("generationInstant"));
        assertEquals(true, generate.attributes().get("success"));
        assertNotNull(generate.attributes().get("otp"));
    }

    @Test
    void inlineValidationWithVerboseCapturesTrace() {
        Instant evaluationInstant = clock.instant();
        TotpDescriptor descriptor = TotpDescriptor.create("inline", SECRET, TotpHashAlgorithm.SHA256, 8, STEP, DRIFT);
        String otp = TotpGenerator.generate(descriptor, evaluationInstant);

        TotpEvaluationApplicationService.EvaluationCommand.Inline command =
                new TotpEvaluationApplicationService.EvaluationCommand.Inline(
                        SECRET.asHex(),
                        TotpHashAlgorithm.SHA256,
                        8,
                        STEP,
                        otp,
                        DRIFT,
                        evaluationInstant,
                        Optional.empty());

        TotpEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("totp.evaluate.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));

        assertEquals(2, trace.steps().size());
        var descriptorStep = trace.steps().get(0);
        assertEquals("normalize.input", descriptorStep.id());
        assertEquals("TotpDescriptor.create", descriptorStep.detail());
        assertEquals(
                TotpHashAlgorithm.SHA256.name(), descriptorStep.attributes().get("algorithm"));
        assertEquals(8, descriptorStep.attributes().get("digits"));

        var validate = trace.steps().get(1);
        assertEquals("validate.otp", validate.id());
        assertEquals("TotpValidator.verify", validate.detail());
        assertEquals(true, validate.attributes().get("valid"));
        assertEquals(0, validate.attributes().get("matchedSkewSteps"));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        saveStoredCredential();
        TotpEvaluationApplicationService.EvaluationCommand.Stored command =
                new TotpEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID, "", DRIFT, clock.instant(), Optional.empty());

        TotpEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

        assertTrue(result.verboseTrace().isEmpty(), "trace should be absent");
    }

    private void saveStoredCredential() {
        TotpDescriptor descriptor =
                TotpDescriptor.create(CREDENTIAL_ID, SECRET, TotpHashAlgorithm.SHA1, 6, STEP, DRIFT);
        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
        store.save(credential);
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
            return List.copyOf(store.values());
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
