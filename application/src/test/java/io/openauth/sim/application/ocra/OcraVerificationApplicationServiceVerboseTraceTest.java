package io.openauth.sim.application.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OcraVerificationApplicationServiceVerboseTraceTest {

    private static final String SUITE = "OCRA-1:HOTP-SHA1-6:QN08";
    private static final String SHARED_SECRET = "3132333435363738393031323334353637383930";
    private static final String CHALLENGE = "12345678";
    private static final String CREDENTIAL_ID = "stored-ocra";
    private static final HexFormat HEX = HexFormat.of();

    private OcraCredentialDescriptor descriptor;
    private InMemoryCredentialStore store;
    private MockCredentialResolver resolver;
    private OcraVerificationApplicationService service;
    private String expectedOtp;

    @BeforeEach
    void setUp() {
        OcraCredentialFactory factory = new OcraCredentialFactory();
        descriptor = factory.createDescriptor(new OcraCredentialRequest(
                CREDENTIAL_ID,
                SUITE,
                SHARED_SECRET,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "verbose-test")));
        resolver = new MockCredentialResolver(descriptor);
        store = new InMemoryCredentialStore();
        store.save(VersionedCredentialRecordMapper.toCredential(
                new OcraCredentialPersistenceAdapter().serialize(descriptor)));
        service = new OcraVerificationApplicationService(resolver, store);
        expectedOtp = OcraResponseCalculator.generate(
                descriptor, new OcraExecutionContext(null, CHALLENGE, null, null, null, null, null));
    }

    @Test
    void storedVerificationWithVerboseEmitsTrace() {
        var command = new OcraVerificationApplicationService.VerificationCommand.Stored(
                CREDENTIAL_ID, expectedOtp, CHALLENGE, null, null, null, null, null, null);

        var result = service.verify(command, true);

        assertTrue(result.verboseTrace().isPresent());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        assertEquals("ocra.verify.stored", trace.operation());
        assertEquals("OCRA", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals(CREDENTIAL_ID, trace.metadata().get("credentialId"));
        assertEquals(SUITE, trace.metadata().get("suite"));
        assertEquals("educational", trace.metadata().get("tier"));

        VerboseTrace.TraceStep normalize = findStep(trace, "normalize.request");
        assertEquals(CHALLENGE, normalize.attributes().get("challenge"));

        VerboseTrace.TraceStep resolve = findStep(trace, "resolve.credential");
        assertEquals(CREDENTIAL_ID, resolve.attributes().get("credentialId"));

        VerboseTrace.TraceStep parse = findStep(trace, "parse.suite");
        assertEquals(SUITE, parse.attributes().get("suite.value"));

        VerboseTrace.TraceStep inputs = findStep(trace, "normalize.inputs");
        assertEquals(expectedSecretHash(), inputs.attributes().get("secret.hash"));
        assertEquals(expectedQuestionHex(), inputs.attributes().get("question.hex"));

        VerboseTrace.TraceStep message = findStep(trace, "assemble.message");
        assertEquals(expectedMessageHex(), message.attributes().get("message.hex"));
        assertEquals(expectedSuiteLengthBytes(), message.attributes().get("segment.0.suite.len.bytes"));
        assertEquals(1, message.attributes().get("segment.1.separator.len.bytes"));
        assertEquals(expectedQuestionLengthBytes(), message.attributes().get("segment.3.question.len.bytes"));
        assertEquals(expectedMessageLengthBytes(), message.attributes().get("message.len.bytes"));
        assertEquals(expectedMessageSha256(), message.attributes().get("message.sha256"));
        assertEquals(expectedPartsCount(), message.attributes().get("parts.count"));
        assertEquals(expectedPartsOrderQuestionOnly(), message.attributes().get("parts.order"));

        VerboseTrace.TraceStep hmac = findStep(trace, "compute.hmac");
        assertEquals(expectedCanonicalAlgorithm(), hmac.attributes().get("alg"));
        assertEquals(expectedCanonicalAlgorithm(), hmac.detail());
        assertEquals(expectedHmacHex(), hmac.attributes().get("hmac.hex"));

        VerboseTrace.TraceStep compare = findStep(trace, "compare.expected");
        assertEquals(expectedOtp, compare.attributes().get("compare.expected"));
        assertEquals(expectedOtp, compare.attributes().get("compare.supplied"));
        assertEquals(true, compare.attributes().get("compare.match"));
    }

    @Test
    void inlineVerificationWithVerboseEmitsTrace() {
        var command = new OcraVerificationApplicationService.VerificationCommand.Inline(
                "inline", SUITE, SHARED_SECRET, expectedOtp, CHALLENGE, null, null, null, null, null, null, null);

        var result = service.verify(command, true);

        assertTrue(result.verboseTrace().isPresent());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        assertEquals("ocra.verify.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));
        assertEquals(SUITE, trace.metadata().get("suite"));

        VerboseTrace.TraceStep create = findStep(trace, "create.descriptor");
        assertEquals("inline", create.attributes().get("identifier"));

        VerboseTrace.TraceStep inputs = findStep(trace, "normalize.inputs");
        assertEquals(expectedSecretHash(), inputs.attributes().get("secret.hash"));

        VerboseTrace.TraceStep message = findStep(trace, "assemble.message");
        assertEquals(expectedQuestionLengthBytes(), message.attributes().get("segment.3.question.len.bytes"));
        assertEquals(expectedMessageLengthBytes(), message.attributes().get("message.len.bytes"));
        assertEquals(expectedPartsCount(), message.attributes().get("parts.count"));
        assertEquals(expectedPartsOrderQuestionOnly(), message.attributes().get("parts.order"));

        VerboseTrace.TraceStep compare = findStep(trace, "compare.expected");
        assertEquals(expectedOtp, compare.attributes().get("compare.expected"));
        assertEquals(expectedOtp, compare.attributes().get("compare.supplied"));
        assertEquals(true, compare.attributes().get("compare.match"));
    }

    @Test
    void verboseDisabledOmitsTrace() {
        var command = new OcraVerificationApplicationService.VerificationCommand.Stored(
                CREDENTIAL_ID, expectedOtp, CHALLENGE, null, null, null, null, null, null);

        var result = service.verify(command);

        assertTrue(result.verboseTrace().isEmpty());
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static String expectedSecretHash() {
        return sha256Digest(hexBytes(SHARED_SECRET));
    }

    private static String expectedQuestionHex() {
        return new java.math.BigInteger(CHALLENGE, 10).toString(16).toLowerCase(Locale.ROOT);
    }

    private static String expectedQuestionPadded() {
        String base = expectedQuestionHex();
        StringBuilder builder = new StringBuilder(base);
        while (builder.length() < 256) {
            builder.append('0');
        }
        return builder.toString();
    }

    private static String expectedMessageHex() {
        return hexString(expectedMessageBytes());
    }

    private static String expectedMessageSha256() {
        return sha256Digest(expectedMessageBytes());
    }

    private static String expectedHmacHex() {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(hexBytes(SHARED_SECRET), "RAW"));
            return hexString(mac.doFinal(expectedMessageBytes()));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute expected HMAC", ex);
        }
    }

    private static byte[] expectedMessageBytes() {
        byte[] suiteBytes = SUITE.getBytes(StandardCharsets.US_ASCII);
        byte[] questionBytes = hexBytes(expectedQuestionPadded());
        byte[] message = new byte[suiteBytes.length + 1 + questionBytes.length];
        System.arraycopy(suiteBytes, 0, message, 0, suiteBytes.length);
        message[suiteBytes.length] = 0x00;
        System.arraycopy(questionBytes, 0, message, suiteBytes.length + 1, questionBytes.length);
        return message;
    }

    private static int expectedSuiteLengthBytes() {
        return SUITE.getBytes(StandardCharsets.US_ASCII).length;
    }

    private static int expectedQuestionLengthBytes() {
        return expectedQuestionPadded().length() / 2;
    }

    private static int expectedMessageLengthBytes() {
        return expectedMessageBytes().length;
    }

    private static int expectedPartsCount() {
        return 3;
    }

    private static String expectedPartsOrderQuestionOnly() {
        return "[suite, sep, question]";
    }

    private static String expectedCanonicalAlgorithm() {
        return "HMAC-SHA-1";
    }

    private static byte[] hexBytes(String hex) {
        return HEX.parseHex(hex);
    }

    private static String hexString(byte[] bytes) {
        return HEX.formatHex(bytes).toLowerCase(Locale.ROOT);
    }

    private static String sha256Digest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hexString(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static final class MockCredentialResolver implements OcraVerificationApplicationService.CredentialResolver {

        private final OcraCredentialDescriptor descriptor;

        private MockCredentialResolver(OcraCredentialDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Optional<OcraCredentialDescriptor> findById(String credentialId) {
            return CREDENTIAL_ID.equals(credentialId) ? Optional.of(descriptor) : Optional.empty();
        }
    }

    private static final class InMemoryCredentialStore implements CredentialStore {

        private final Map<String, Credential> storage = new LinkedHashMap<>();

        @Override
        public void save(Credential credential) {
            storage.put(credential.name(), credential);
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(storage.get(name));
        }

        @Override
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(storage.values());
        }

        @Override
        public boolean delete(String name) {
            return storage.remove(name) != null;
        }

        @Override
        public void close() {
            storage.clear();
        }
    }
}
