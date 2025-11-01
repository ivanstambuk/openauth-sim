package io.openauth.sim.application.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.trace.VerboseTrace;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OcraEvaluationApplicationServiceVerboseTraceTest {

    private static final String SUITE_VALUE = "OCRA-1:HOTP-SHA1-6:QN08";
    private static final String CHALLENGE = "12345678";
    private static final String SHARED_SECRET = "3132333435363738393031323334353637383930";
    private static final HexFormat HEX = HexFormat.of();

    private OcraCredentialDescriptor storedDescriptor;
    private MockCredentialResolver credentialResolver;
    private OcraEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        OcraCredentialFactory factory = new OcraCredentialFactory();
        storedDescriptor = factory.createDescriptor(new OcraCredentialRequest(
                "stored-ocra",
                SUITE_VALUE,
                SHARED_SECRET,
                io.openauth.sim.core.model.SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "stored")));
        credentialResolver = new MockCredentialResolver(storedDescriptor);
        service = new OcraEvaluationApplicationService(
                Clock.fixed(Instant.parse("2025-10-22T12:00:00Z"), ZoneOffset.UTC), credentialResolver);
    }

    @Test
    void storedEvaluationWithVerboseCapturesTrace() {
        OcraEvaluationApplicationService.EvaluationCommand.Stored command =
                new OcraEvaluationApplicationService.EvaluationCommand.Stored(
                        "stored-ocra", CHALLENGE, "", "", "", "", "", null, 0, 0);

        OcraEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertEquals(1, result.previews().size());
        assertEquals(0, result.previews().get(0).delta());
        assertEquals(result.otp(), result.previews().get(0).otp());
        assertTrue(result.verboseTrace().isPresent());
        VerboseTrace trace = result.verboseTrace().orElseThrow();

        assertEquals("ocra.evaluate.stored", trace.operation());
        assertEquals("OCRA", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals("stored-ocra", trace.metadata().get("credentialId"));
        assertEquals(SUITE_VALUE, trace.metadata().get("suite"));

        VerboseTrace.TraceStep parseSuite = findStep(trace, "parse.suite");
        assertEquals("rfc6287§5.1", parseSuite.specAnchor());
        assertEquals(SUITE_VALUE, parseSuite.attributes().get("suite.value"));
        assertEquals("SHA1", parseSuite.attributes().get("crypto.hash"));
        assertEquals(6, parseSuite.attributes().get("crypto.digits"));
        assertEquals(true, parseSuite.attributes().get("input.challenge"));
        assertEquals("NUMERIC", parseSuite.attributes().get("challenge.format"));
        assertEquals(8, parseSuite.attributes().get("challenge.length"));
        assertFalse(parseSuite.attributes().containsKey("input.counter"));

        VerboseTrace.TraceStep normalize = findStep(trace, "normalize.inputs");
        assertEquals("rfc6287§5.2", normalize.specAnchor());
        assertEquals(expectedSecretHash(), normalize.attributes().get("secret.hash"));
        assertEquals(expectedQuestionHex(), normalize.attributes().get("question.hex"));
        assertEquals(CHALLENGE, normalize.attributes().get("question.source"));
        assertEquals("", normalize.attributes().getOrDefault("session.hex", ""));
        assertFalse(normalize.attributes().containsKey("counter.hex"));

        VerboseTrace.TraceStep assemble = findStep(trace, "assemble.message");
        assertEquals("rfc6287§6", assemble.specAnchor());
        assertEquals(asciiHex(SUITE_VALUE), assemble.attributes().get("segment.0.suite"));
        assertEquals(expectedSuiteLengthBytes(), assemble.attributes().get("segment.0.suite.len.bytes"));
        assertEquals("00", assemble.attributes().get("segment.1.separator"));
        assertEquals(1, assemble.attributes().get("segment.1.separator.len.bytes"));
        assertEquals(expectedQuestionPadded(), assemble.attributes().get("segment.3.question"));
        assertEquals(expectedQuestionLengthBytes(), assemble.attributes().get("segment.3.question.len.bytes"));
        assertEquals(expectedMessageHex(), assemble.attributes().get("message.hex"));
        assertEquals(expectedMessageLengthBytes(), assemble.attributes().get("message.len.bytes"));
        assertEquals(expectedMessageSha256(), assemble.attributes().get("message.sha256"));
        assertEquals(expectedPartsCount(), assemble.attributes().get("parts.count"));
        assertEquals(expectedPartsOrderQuestionOnly(), assemble.attributes().get("parts.order"));

        VerboseTrace.TraceStep hmacStep = findStep(trace, "compute.hmac");
        assertEquals("rfc6287§7", hmacStep.specAnchor());
        assertEquals(expectedCanonicalAlgorithm(), hmacStep.attributes().get("alg"));
        assertEquals(expectedCanonicalAlgorithm(), hmacStep.detail());
        assertEquals(expectedSecretHash(), hmacStep.attributes().get("key.hash"));
        assertEquals(expectedMessageHex(), hmacStep.attributes().get("message.hex"));
        assertEquals(expectedHmacHex(), hmacStep.attributes().get("hmac.hex"));

        VerboseTrace.TraceStep truncate = findStep(trace, "truncate.dynamic");
        assertEquals("rfc6287§7", truncate.specAnchor());
        assertEquals(expectedOffset(), truncate.attributes().get("offset"));
        assertEquals(expectedDbc(), truncate.attributes().get("dynamic_binary_code"));

        VerboseTrace.TraceStep reduce = findStep(trace, "mod.reduce");
        assertEquals("rfc6287§7", reduce.specAnchor());
        assertEquals(6, reduce.attributes().get("digits"));
        assertEquals(result.otp(), reduce.attributes().get("otp"));
    }

    @Test
    void inlineEvaluationWithVerboseCapturesTrace() {
        OcraEvaluationApplicationService.EvaluationCommand.Inline command =
                new OcraEvaluationApplicationService.EvaluationCommand.Inline(
                        "inline-ocra", SUITE_VALUE, SHARED_SECRET, CHALLENGE, "", "", "", "", "", null, null, 0, 0);

        OcraEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertEquals(1, result.previews().size());
        assertEquals(0, result.previews().get(0).delta());
        assertEquals(result.otp(), result.previews().get(0).otp());
        assertTrue(result.verboseTrace().isPresent());
        VerboseTrace trace = result.verboseTrace().orElseThrow();

        assertEquals("ocra.evaluate.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));

        VerboseTrace.TraceStep createDescriptor = findStep(trace, "create.descriptor");
        assertNotNull(createDescriptor);

        VerboseTrace.TraceStep parseSuite = findStep(trace, "parse.suite");
        assertEquals(SUITE_VALUE, parseSuite.attributes().get("suite.value"));

        VerboseTrace.TraceStep normalize = findStep(trace, "normalize.inputs");
        assertEquals(expectedSecretHash(), normalize.attributes().get("secret.hash"));
        assertEquals(expectedQuestionHex(), normalize.attributes().get("question.hex"));

        VerboseTrace.TraceStep assemble = findStep(trace, "assemble.message");
        assertEquals(expectedMessageHex(), assemble.attributes().get("message.hex"));
        assertEquals(expectedQuestionLengthBytes(), assemble.attributes().get("segment.3.question.len.bytes"));
        assertEquals(expectedMessageLengthBytes(), assemble.attributes().get("message.len.bytes"));
        assertEquals(expectedPartsCount(), assemble.attributes().get("parts.count"));
        assertEquals(expectedPartsOrderQuestionOnly(), assemble.attributes().get("parts.order"));

        VerboseTrace.TraceStep reduce = findStep(trace, "mod.reduce");
        assertEquals(result.otp(), reduce.attributes().get("otp"));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        OcraEvaluationApplicationService.EvaluationCommand.Stored command =
                new OcraEvaluationApplicationService.EvaluationCommand.Stored(
                        "stored-ocra", CHALLENGE, "", "", "", "", "", null, 0, 0);

        OcraEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

        assertTrue(result.verboseTrace().isEmpty());
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static String expectedQuestionHex() {
        return new BigInteger(CHALLENGE, 10).toString(16).toLowerCase(Locale.ROOT);
    }

    private static String expectedQuestionPadded() {
        String base = expectedQuestionHex();
        StringBuilder builder = new StringBuilder(base);
        while (builder.length() < 256) {
            builder.append('0');
        }
        return builder.toString();
    }

    private static String expectedSecretHash() {
        return sha256Digest(hexBytes(SHARED_SECRET));
    }

    private static String expectedMessageHex() {
        return hexString(expectedMessageBytes());
    }

    private static String expectedMessageSha256() {
        return sha256Digest(expectedMessageBytes());
    }

    private static String expectedHmacHex() {
        return hexString(expectedHmacBytes());
    }

    private static int expectedOffset() {
        byte[] hmac = expectedHmacBytes();
        return hmac[hmac.length - 1] & 0x0F;
    }

    private static int expectedDbc() {
        byte[] hmac = expectedHmacBytes();
        int offset = expectedOffset();
        return ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);
    }

    private static byte[] expectedMessageBytes() {
        byte[] suiteBytes = SUITE_VALUE.getBytes(StandardCharsets.US_ASCII);
        byte[] questionBytes = hexBytes(expectedQuestionPadded());
        byte[] message = new byte[suiteBytes.length + 1 + questionBytes.length];
        System.arraycopy(suiteBytes, 0, message, 0, suiteBytes.length);
        message[suiteBytes.length] = 0x00;
        System.arraycopy(questionBytes, 0, message, suiteBytes.length + 1, questionBytes.length);
        return message;
    }

    private static int expectedSuiteLengthBytes() {
        return SUITE_VALUE.getBytes(StandardCharsets.US_ASCII).length;
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

    private static byte[] expectedHmacBytes() {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(hexBytes(SHARED_SECRET), "RAW"));
            return mac.doFinal(expectedMessageBytes());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute expected HMAC", ex);
        }
    }

    private static byte[] hexBytes(String hex) {
        return HEX.parseHex(hex);
    }

    private static String hexString(byte[] bytes) {
        return HEX.formatHex(bytes).toLowerCase(Locale.ROOT);
    }

    private static String asciiHex(String value) {
        return hexString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256Digest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hexString(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static final class MockCredentialResolver implements OcraEvaluationApplicationService.CredentialResolver {

        private final OcraEvaluationApplicationService.ResolvedCredential resolvedCredential;

        private MockCredentialResolver(OcraCredentialDescriptor descriptor) {
            this.resolvedCredential = new OcraEvaluationApplicationService.ResolvedCredential(descriptor);
        }

        @Override
        public Optional<OcraEvaluationApplicationService.ResolvedCredential> findById(String credentialId) {
            if ("stored-ocra".equals(credentialId)) {
                return Optional.of(resolvedCredential);
            }
            return Optional.empty();
        }
    }
}
