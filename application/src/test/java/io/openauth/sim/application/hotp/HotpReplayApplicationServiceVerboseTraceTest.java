package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.hotp.HotpReplayApplicationService.ReplayCommand;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HotpReplayApplicationServiceVerboseTraceTest {

    private static final String CREDENTIAL_ID = "hotp-trace-replay";
    private static final HotpHashAlgorithm ALGORITHM = HotpHashAlgorithm.SHA1;
    private static final int DIGITS = 6;
    private static final SecretMaterial SECRET = SecretMaterial.fromHex("3132333435363738393031323334353637383930");

    private InMemoryCredentialStore credentialStore;
    private HotpReplayApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        service = new HotpReplayApplicationService(credentialStore);
    }

    @Test
    void storedReplayVerboseTraceIncludesWindowDetails() {
        long counter = 0L;
        credentialStore.save(Credential.create(CREDENTIAL_ID, CredentialType.OATH_HOTP, SECRET, attributes(counter)));

        HotpDescriptor descriptor = HotpDescriptor.create(CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS);
        String otp = HotpGenerator.generate(descriptor, counter);

        var result = service.replay(new ReplayCommand.Stored(CREDENTIAL_ID, otp), true);
        VerboseTrace trace = result.verboseTrace().orElseThrow();

        assertEquals("hotp.replay.stored", trace.operation());
        assertEquals("HOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals("educational", trace.metadata().get("tier"));
        assertTrue(trace.steps().size() >= 3, "expected normalize/search/decision steps");

        VerboseTrace.TraceStep normalize = findStep(trace, "normalize.input");
        assertEquals("rfc4226§5.1", normalize.specAnchor());
        assertEquals("replay.stored", normalize.attributes().get("op"));
        assertEquals(ALGORITHM.name(), normalize.attributes().get("alg"));
        assertEquals(DIGITS, normalize.attributes().get("digits"));
        assertEquals(otp, normalize.attributes().get("otp.provided"));
        assertEquals(counter, normalize.attributes().get("counter.hint"));
        Object window = normalize.attributes().get("window");
        assertTrue(window instanceof Number, "window attribute should be numeric");
        assertEquals(10, ((Number) window).intValue());

        VerboseTrace.TraceStep search = findStep(trace, "search.window");
        assertEquals("rfc4226§5.4", search.specAnchor());
        assertEquals(
                "[" + (counter - 10) + ", " + (counter + 10) + "]",
                search.attributes().get("window.range"));
        assertEquals("ascending", search.attributes().get("order"));
        assertTrue(search.attributes().containsKey("attempt." + counter + ".otp"));
        assertTrue(search.attributes().get("match.marker.begin").toString().contains("-- begin match.derivation --"));
        assertTrue(search.attributes().containsKey("match.mod.reduce.otp.string.leftpad"));

        VerboseTrace.TraceStep decision = findStep(trace, "decision");
        assertEquals(Boolean.TRUE, decision.attributes().get("verify.match"));
        assertEquals(counter, decision.attributes().get("matched.counter"));
        assertEquals(counter + 1, decision.attributes().get("next.expected.counter"));
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static Map<String, String> attributes(long counter) {
        return Map.of(
                "hotp.algorithm", ALGORITHM.name(),
                "hotp.digits", Integer.toString(DIGITS),
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
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(data.values());
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
