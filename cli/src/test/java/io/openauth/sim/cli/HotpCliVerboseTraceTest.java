package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures.HotpJsonVector;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class HotpCliVerboseTraceTest {

    private static final String CREDENTIAL_ID = "hotp-trace-demo";

    @TempDir
    Path tempDir;

    @Test
    void evaluateStoredEmitsVerboseTraceWhenEnabled() throws Exception {
        Path database = tempDir.resolve("hotp-trace.db");
        CommandHarness harness = CommandHarness.create(database);
        HotpJsonVector vector = vector(0L);
        harness.seed(vector, CREDENTIAL_ID);

        int exitCode = harness.execute("evaluate", "--credential-id", CREDENTIAL_ID, "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("=== End Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=hotp.evaluate.stored"), stdout);
        assertTrue(stdout.contains("metadata.protocol=HOTP"), stdout);
        assertTrue(stdout.contains("metadata.mode=stored"), stdout);
        assertTrue(stdout.contains("metadata.tier=educational"), stdout);
        assertTrue(stdout.contains("metadata.credentialId=" + CREDENTIAL_ID), stdout);
        assertTrue(stdout.contains("metadata.counter.next=1"), stdout);
        assertTrue(stdout.toLowerCase(Locale.ROOT).contains("step"), stdout);
        assertTrue(stdout.contains("step.1: normalize.input"), stdout);
        assertTrue(stdout.contains("  op = evaluate.stored"), stdout);
        assertTrue(stdout.contains("step.2: prepare.counter"), stdout);
        assertTrue(stdout.contains("step.3: hmac.compute"), stdout);
        assertTrue(stdout.contains("step.4: truncate.dynamic"), stdout);
        assertTrue(stdout.contains("step.5: mod.reduce"), stdout);
        assertTrue(stdout.contains("step.6: result"), stdout);
        String secretSha256 = sha256Digest(vector.secret().value());
        long counter = vector.counter();
        byte[] counterBytes = longBytes(counter);
        String counterHex = hex(counterBytes);
        String keyPrimeSha256 = sha256Digest(paddedKey(vector));
        byte[] computedInnerHash = innerHash(vector, counterBytes);
        byte[] hmac = computeHmac(vector, counterBytes, computedInnerHash);
        int hmacOffset = offset(hmac);
        byte[] slice = Arrays.copyOfRange(hmac, hmacOffset, hmacOffset + 4);
        int dbc = dynamicBinaryCode(slice);
        assertTrue(stdout.contains("  secret.sha256 = " + secretSha256), stdout);
        assertTrue(stdout.contains("  counter.bytes.big_endian = " + counterHex), stdout);
        assertTrue(stdout.contains("  key'.sha256 = " + keyPrimeSha256), stdout);
        assertTrue(stdout.contains("  inner.hash = " + hex(computedInnerHash)), stdout);
        assertTrue(stdout.contains("  hmac.final = " + hex(hmac)), stdout);
        assertTrue(stdout.contains("  slice.bytes = " + hex(slice)), stdout);
        assertTrue(stdout.contains("  dynamic_binary_code.31bit.big_endian = " + dbc), stdout);
        assertTrue(stdout.contains("  last.byte = " + formatByte(hmac[hmac.length - 1])), stdout);
        assertTrue(stdout.contains("  output.otp = " + vector.otp()), stdout);
    }

    @Test
    void evaluateStoredKeepsVerboseTraceDisabledByDefault() throws Exception {
        Path database = tempDir.resolve("hotp-default.db");
        CommandHarness harness = CommandHarness.create(database);
        HotpJsonVector vector = vector(0L);
        harness.seed(vector, CREDENTIAL_ID);

        int exitCode = harness.execute("evaluate", "--credential-id", CREDENTIAL_ID);

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertFalse(stdout.contains("=== Verbose Trace ==="), stdout);
        assertFalse(stdout.contains("operation=hotp.evaluate.stored"), stdout);
    }

    @Test
    void evaluateInlineEmitsVerboseTrace() {
        CommandHarness harness = CommandHarness.create(tempDir.resolve("hotp-inline.db"));
        HotpJsonVector vector = vector(1L);

        int exitCode = harness.execute(
                "evaluate",
                "--secret",
                vector.secret().asHex(),
                "--digits",
                String.valueOf(vector.digits()),
                "--counter",
                String.valueOf(vector.counter()),
                "--algorithm",
                vector.algorithm().name(),
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("operation=hotp.evaluate.inline"), stdout);
        assertTrue(stdout.contains("metadata.mode=inline"), stdout);
        assertTrue(stdout.contains("metadata.protocol=HOTP"), stdout);
        assertTrue(stdout.contains("generatedOtp=" + vector.otp()), stdout);
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("=== End Verbose Trace ==="), stdout);
    }

    private static HotpJsonVector vector(long counter) {
        return HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == 6 && v.counter() == counter)
                .findFirst()
                .orElseThrow();
    }

    private static final class CommandHarness {

        private final HotpCli cli;
        private final CommandLine commandLine;
        private final Path database;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.database = database.toAbsolutePath();
            this.cli = new HotpCli();
            cli.overrideDatabase(this.database);
            this.commandLine = new CommandLine(cli);
            commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        }

        static CommandHarness create(Path database) {
            return new CommandHarness(database);
        }

        int execute(String... args) {
            stdout.reset();
            stderr.reset();
            return commandLine.execute(args);
        }

        String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }

        void seed(HotpJsonVector vector, String credentialId) {
            stdout.reset();
            stderr.reset();
            int exitCode = commandLine.execute(
                    "import",
                    "--credential-id",
                    credentialId,
                    "--secret",
                    vector.secret().asHex(),
                    "--digits",
                    String.valueOf(vector.digits()),
                    "--counter",
                    String.valueOf(vector.counter()),
                    "--algorithm",
                    vector.algorithm().name());
            if (exitCode != CommandLine.ExitCode.OK) {
                throw new IllegalStateException(
                        "Failed to import HOTP vector: exitCode=" + exitCode + " stderr=" + stderr());
            }
        }
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

    private static byte[] paddedKey(HotpJsonVector vector) {
        int blockLength = blockLength(vector.algorithm());
        byte[] key = new byte[blockLength];
        byte[] source = vector.secret().value();
        System.arraycopy(source, 0, key, 0, Math.min(source.length, key.length));
        return key;
    }

    private static byte[] innerHash(HotpJsonVector vector, byte[] counterBytes) {
        byte[] paddedKey = paddedKey(vector);
        byte[] innerPad = xorWith(paddedKey, (byte) 0x36);
        byte[] innerInput = concat(innerPad, counterBytes);
        return shaDigest(digestAlgorithm(vector.algorithm()), innerInput);
    }

    private static byte[] computeHmac(HotpJsonVector vector, byte[] counterBytes, byte[] innerHash) {
        byte[] paddedKey = paddedKey(vector);
        byte[] outerPad = xorWith(paddedKey, (byte) 0x5c);
        byte[] outerInput = concat(outerPad, innerHash);
        return shaDigest(digestAlgorithm(vector.algorithm()), outerInput);
    }

    private static int offset(byte[] hmac) {
        return hmac[hmac.length - 1] & 0x0F;
    }

    private static int dynamicBinaryCode(byte[] slice) {
        return ((slice[0] & 0x7F) << 24) | ((slice[1] & 0xFF) << 16) | ((slice[2] & 0xFF) << 8) | (slice[3] & 0xFF);
    }

    private static byte[] xorWith(byte[] input, byte padByte) {
        byte[] result = Arrays.copyOf(input, input.length);
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

    private static byte[] shaDigest(String algorithm, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return digest.digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unsupported digest algorithm: " + algorithm, ex);
        }
    }

    private static int blockLength(HotpHashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA1, SHA256 -> 64;
            case SHA512 -> 128;
        };
    }

    private static String digestAlgorithm(HotpHashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA1 -> "SHA-1";
            case SHA256 -> "SHA-256";
            case SHA512 -> "SHA-512";
        };
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
}
