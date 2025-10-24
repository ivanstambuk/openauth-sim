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
import io.openauth.sim.core.trace.VerboseTrace;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

        var result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent(), "expected verbose trace");
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        assertEquals("totp.evaluate.stored", trace.operation());
        assertEquals("TOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals("educational", trace.metadata().get("tier"));
        assertEquals(CREDENTIAL_ID, trace.metadata().get("credentialId"));

        VerboseTrace.TraceStep resolve = findStep(trace, "resolve.credential");
        assertEquals("rfc4226§5.1", resolve.specAnchor());
        assertEquals(CREDENTIAL_ID, resolve.attributes().get("credentialId"));
        assertEquals(TotpHashAlgorithm.SHA1.name(), resolve.attributes().get("algorithm"));
        assertEquals(6, resolve.attributes().get("digits"));
        assertEquals(STEP.getSeconds(), longAttr(resolve, "stepSeconds"));
        assertEquals(DRIFT.backwardSteps(), longAttr(resolve, "drift.backward"));
        assertEquals(DRIFT.forwardSteps(), longAttr(resolve, "drift.forward"));
        assertEquals(sha256Digest(SECRET.value()), resolve.attributes().get("secret.hash"));

        long epochSeconds = clock.instant().getEpochSecond();
        long stepSeconds = STEP.getSeconds();
        long counter = epochSeconds / stepSeconds;
        long stepStart = counter * stepSeconds;
        long stepEnd = stepStart + stepSeconds;
        byte[] counterBytes = longBytes(counter);
        String counterHex = hex(counterBytes);

        VerboseTrace.TraceStep derive = findStep(trace, "derive.time-counter");
        assertEquals("rfc6238§4.2", derive.specAnchor());
        assertEquals(epochSeconds, longAttr(derive, "epoch.seconds"));
        assertEquals(0L, longAttr(derive, "t0.seconds"));
        assertEquals(stepSeconds, longAttr(derive, "step.seconds"));
        assertEquals(counter, longAttr(derive, "time.counter.int"));
        assertEquals(String.format("%016x", counter), derive.attributes().get("time.counter.hex"));
        assertEquals(stepStart, longAttr(derive, "step.start.seconds"));
        assertEquals(stepEnd, longAttr(derive, "step.end.seconds"));

        VerboseTrace.TraceStep window = findStep(trace, "evaluate.window");
        assertEquals("rfc6238§4.1", window.specAnchor());
        assertEquals(DRIFT.backwardSteps(), longAttr(window, "skew.backward"));
        assertEquals(DRIFT.forwardSteps(), longAttr(window, "skew.forward"));
        assertNotNull(window.attributes().get("offset.0.otp"));

        String hmacHex = hex(hmac(TotpHashAlgorithm.SHA1, SECRET.value(), counterBytes));
        int offset = dynamicOffset(hmacHex);
        int dbc = dynamicBinaryCode(hmacHex, offset);

        VerboseTrace.TraceStep compute = findStep(trace, "compute.hmac");
        assertEquals("rfc4226§5.2", compute.specAnchor());
        assertEquals(counterHex, compute.attributes().get("message.hex"));
        assertEquals(hmacHex, compute.attributes().get("hmac.hex"));

        VerboseTrace.TraceStep truncate = findStep(trace, "truncate.dynamic");
        assertEquals("rfc4226§5.3", truncate.specAnchor());
        assertEquals(offset, truncate.attributes().get("offset"));
        assertEquals(dbc, truncate.attributes().get("dbc"));

        VerboseTrace.TraceStep reduce = findStep(trace, "mod.reduce");
        assertEquals("rfc4226§5.4", reduce.specAnchor());
        assertEquals(6, reduce.attributes().get("digits"));
        assertEquals(String.format("%06d", dbc % 1_000_000), reduce.attributes().get("otp"));
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

        var result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        assertEquals("totp.evaluate.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));

        VerboseTrace.TraceStep normalize = findStep(trace, "normalize.input");
        assertEquals("rfc4226§5.1", normalize.specAnchor());
        assertEquals(TotpHashAlgorithm.SHA256.name(), normalize.attributes().get("algorithm"));
        assertEquals(8, longAttr(normalize, "digits"));
        assertEquals(sha256Digest(SECRET.value()), normalize.attributes().get("secret.hash"));

        long counter = evaluationInstant.getEpochSecond() / STEP.getSeconds();
        byte[] counterBytes = longBytes(counter);
        String hmacHex = hex(hmac(TotpHashAlgorithm.SHA256, SECRET.value(), counterBytes));

        VerboseTrace.TraceStep derive = findStep(trace, "derive.time-counter");
        assertEquals(counter, longAttr(derive, "time.counter.int"));

        VerboseTrace.TraceStep compute = findStep(trace, "compute.hmac");
        assertEquals(hmacHex, compute.attributes().get("hmac.hex"));

        VerboseTrace.TraceStep validate = findStep(trace, "validate.otp");
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

        var result = service.evaluate(command);

        assertTrue(result.verboseTrace().isEmpty(), "trace should be absent");
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static long longAttr(VerboseTrace.TraceStep step, String key) {
        Object value = step.attributes().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new AssertionError("Attribute " + key + " is not numeric: " + value);
    }

    private static String sha256Digest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hex(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hmac(TotpHashAlgorithm algorithm, byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithm.macAlgorithm());
            mac.init(new SecretKeySpec(secret, algorithm.macAlgorithm()));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute TOTP HMAC", ex);
        }
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static int dynamicOffset(String hmacHex) {
        byte[] bytes = hexToBytes(hmacHex);
        return bytes[bytes.length - 1] & 0x0F;
    }

    private static int dynamicBinaryCode(String hmacHex, int offset) {
        byte[] bytes = hexToBytes(hmacHex);
        return ((bytes[offset] & 0x7F) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
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
