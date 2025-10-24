package io.openauth.sim.application.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.trace.VerboseTrace;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HotpEvaluationApplicationServiceVerboseTraceTest {

    private CredentialStore credentialStore;
    private HotpEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemoryCredentialStore();
        service = new HotpEvaluationApplicationService(credentialStore);
    }

    @Test
    void storedEvaluationWithVerboseCapturesTrace() {
        SecretMaterial secret = SecretMaterial.fromHex("3132333435363738393031323334353637383930");
        credentialStore.save(Credential.create("otp-001", CredentialType.OATH_HOTP, secret, attributes(7L)));

        var command = new HotpEvaluationApplicationService.EvaluationCommand.Stored("otp-001");

        var verboseResult = service.evaluate(command, true);
        assertTrue(verboseResult.verboseTrace().isPresent(), "expected verbose trace when enabled");
        var trace = verboseResult.verboseTrace().orElseThrow();

        assertEquals("hotp.evaluate.stored", trace.operation());
        assertEquals("HOTP", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals("otp-001", trace.metadata().get("credentialId"));
        assertEquals("educational", trace.metadata().get("tier"));
        assertEquals("8", trace.metadata().get("counter.next"));

        assertEquals(6, trace.steps().size(), "trace should expose six HOTP evaluation steps");

        byte[] secretBytes = secret.value();
        byte[] counterBytes = longBytes(7L);
        String secretSha256 = sha256Digest(secretBytes);
        int blockLength = blockLength(HotpHashAlgorithm.SHA1);
        byte[] paddedKey = new byte[blockLength];
        System.arraycopy(secretBytes, 0, paddedKey, 0, secretBytes.length);
        String keyPrimeSha256 = sha256Digest(paddedKey);
        byte[] innerPad = xorWith(paddedKey, (byte) 0x36);
        byte[] innerInput = concat(innerPad, counterBytes);
        byte[] innerHash = shaDigest("SHA-1", innerInput);
        byte[] outerPad = xorWith(paddedKey, (byte) 0x5c);
        byte[] outerInput = concat(outerPad, innerHash);
        byte[] hmac = shaDigest("SHA-1", outerInput);
        int offset = hmac[hmac.length - 1] & 0x0F;
        byte[] slice = Arrays.copyOfRange(hmac, offset, offset + 4);
        int dbc = dynamicBinaryCode(slice);
        int modulus = decimalModulus(6);
        int otpDecimal = dbc % modulus;
        String otpString = String.format("%06d", otpDecimal);

        var normalize = findStep(trace, "normalize.input");
        assertEquals("rfc4226§5.1", normalize.specAnchor());
        assertEquals("evaluate.stored", normalize.attributes().get("op"));
        assertEquals(HotpHashAlgorithm.SHA1.traceLabel(), normalize.attributes().get("alg"));
        assertEquals(6, normalize.attributes().get("digits"));
        assertEquals(7L, normalize.attributes().get("counter.input"));
        assertEquals("hex", normalize.attributes().get("secret.format"));
        assertEquals(secretBytes.length, normalize.attributes().get("secret.len.bytes"));
        assertEquals(secretSha256, normalize.attributes().get("secret.sha256"));

        var prepare = findStep(trace, "prepare.counter");
        assertEquals("rfc4226§5.1", prepare.specAnchor());
        assertEquals(7L, prepare.attributes().get("counter.int"));
        assertEquals(hex(counterBytes), prepare.attributes().get("counter.bytes.big_endian"));

        var compute = findStep(trace, "hmac.compute");
        assertEquals("rfc4226§5.2", compute.specAnchor());
        assertEquals(HotpHashAlgorithm.SHA1.traceLabel(), compute.detail());
        assertEquals(blockLength, compute.attributes().get("hash.block_len"));
        assertEquals("padded", compute.attributes().get("key.mode"));
        assertEquals(keyPrimeSha256, compute.attributes().get("key'.sha256"));
        assertEquals("0x36", compute.attributes().get("ipad.byte"));
        assertEquals("0x5c", compute.attributes().get("opad.byte"));
        assertEquals(hex(innerInput), compute.attributes().get("inner.input"));
        assertEquals(hex(innerHash), compute.attributes().get("inner.hash"));
        assertEquals(hex(outerInput), compute.attributes().get("outer.input"));
        assertEquals(hex(hmac), compute.attributes().get("hmac.final"));

        var truncate = findStep(trace, "truncate.dynamic");
        assertEquals("rfc4226§5.3", truncate.specAnchor());
        assertEquals(formatByte(hmac[hmac.length - 1]), truncate.attributes().get("last.byte"));
        assertEquals(offset, truncate.attributes().get("offset.nibble"));
        assertEquals(hex(slice), truncate.attributes().get("slice.bytes"));
        assertEquals(formatByte((byte) (slice[0] & 0x7F)), truncate.attributes().get("slice.bytes[0]_masked"));
        assertEquals(dbc, truncate.attributes().get("dynamic_binary_code.31bit.big_endian"));

        var reduce = findStep(trace, "mod.reduce");
        assertEquals("rfc4226§5.4", reduce.specAnchor());
        assertEquals(modulus, reduce.attributes().get("modulus"));
        assertEquals(otpDecimal, reduce.attributes().get("otp.decimal"));
        assertEquals(otpString, reduce.attributes().get("otp.string.leftpad"));

        var resultStep = findStep(trace, "result");
        assertEquals(otpString, resultStep.attributes().get("output.otp"));
        assertEquals(8L, resultStep.attributes().get("counter.next"));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        var command = new HotpEvaluationApplicationService.EvaluationCommand.Inline(
                "3132333435363738393031323334353637383930", HotpHashAlgorithm.SHA1, 6, 1L, Map.of());

        var result = service.evaluate(command, false);
        assertTrue(result.verboseTrace().isEmpty(), "trace should be absent when verbose flag is disabled");
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static String sha256Digest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hex(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static byte[] shaDigest(String algorithm, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return digest.digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unsupported digest: " + algorithm, ex);
        }
    }

    private static byte[] xorWith(byte[] key, byte padByte) {
        byte[] result = Arrays.copyOf(key, key.length);
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (result[index] ^ padByte);
        }
        return result;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static int blockLength(HotpHashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA1, SHA256 -> 64;
            case SHA512 -> 128;
        };
    }

    private static int dynamicBinaryCode(byte[] slice) {
        return ((slice[0] & 0x7F) << 24) | ((slice[1] & 0xFF) << 16) | ((slice[2] & 0xFF) << 8) | (slice[3] & 0xFF);
    }

    private static int decimalModulus(int digits) {
        int modulus = 1;
        for (int i = 0; i < digits; i++) {
            modulus *= 10;
        }
        return modulus;
    }

    private static String formatByte(byte value) {
        return String.format("0x%02x", value & 0xFF);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static Map<String, String> attributes(long counter) {
        return Map.of(
                "hotp.algorithm", HotpHashAlgorithm.SHA1.name(),
                "hotp.digits", Integer.toString(6),
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
