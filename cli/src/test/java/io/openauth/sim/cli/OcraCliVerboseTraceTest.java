package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.SecretEncoding;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class OcraCliVerboseTraceTest {

    private static final String SUITE = "OCRA-1:HOTP-SHA1-6:QA08";
    private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
    private static final String CHALLENGE = "12345678";
    private static final String EXPECTED_OTP = computeExpectedOtp();

    @TempDir
    Path tempDir;

    @Test
    void evaluateStoredEmitsVerboseTrace() {
        Path database = tempDir.resolve("ocra-trace.db");
        CommandHarness harness = CommandHarness.create(database);
        harness.seedStoredCredential("ocra-trace");

        int exitCode =
                harness.execute("evaluate", "--credential-id", "ocra-trace", "--challenge", CHALLENGE, "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("=== End Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=ocra.evaluate.stored"), stdout);
        assertTrue(stdout.contains("metadata.credentialId=ocra-trace"), stdout);
        assertTrue(stdout.contains("metadata.tier=educational"), stdout);
        assertTrue(stdout.contains("normalize.request"), stdout);
        assertTrue(stdout.contains("resolve.credential"), stdout);
        assertTrue(stdout.contains("parse.suite"), stdout);
        assertTrue(stdout.contains("normalize.inputs"), stdout);
        assertTrue(stdout.contains("assemble.message"), stdout);
        assertTrue(stdout.contains("message.sha256"), stdout);
        assertTrue(stdout.contains("compute.hmac"), stdout);
        assertTrue(stdout.contains("truncate.dynamic"), stdout);
        assertTrue(stdout.contains("mod.reduce"), stdout);
        assertEquals(
                expectedSecretHash(),
                attributeValue(stdout, "secret.hash"),
                () -> "secret.hash trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedQuestionHex(),
                attributeValue(stdout, "question.hex"),
                () -> "question.hex trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedMessageSha256(),
                attributeValue(stdout, "message.sha256"),
                () -> "message.sha256 trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedHmacHex(),
                attributeValue(stdout, "hmac.hex"),
                () -> "hmac.hex trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedCanonicalAlgorithm(),
                attributeValue(stdout, "alg"),
                () -> "alg trace attribute mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedPartsCount()),
                attributeValue(stdout, "parts.count"),
                () -> "parts.count trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedPartsOrderQuestionOnly(),
                attributeValue(stdout, "parts.order"),
                () -> "parts.order trace attribute mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedSuiteLengthBytes()),
                attributeValue(stdout, "segment.0.suite.len.bytes"),
                () -> "segment.0.suite.len.bytes mismatch\n" + stdout);
        assertEquals(
                "1",
                attributeValue(stdout, "segment.1.separator.len.bytes"),
                () -> "segment.1.separator.len.bytes mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedQuestionLengthBytes()),
                attributeValue(stdout, "segment.3.question.len.bytes"),
                () -> "segment.3.question.len.bytes mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedMessageLengthBytes()),
                attributeValue(stdout, "message.len.bytes"),
                () -> "message.len.bytes mismatch\n" + stdout);
    }

    @Test
    void evaluateStoredOmitsTraceWhenVerboseDisabled() {
        Path database = tempDir.resolve("ocra-default.db");
        CommandHarness harness = CommandHarness.create(database);
        harness.seedStoredCredential("ocra-default");

        int exitCode = harness.execute("evaluate", "--credential-id", "ocra-default", "--challenge", CHALLENGE);

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertFalse(stdout.contains("=== Verbose Trace ==="), stdout);
        assertFalse(stdout.contains("operation=ocra.evaluate.stored"), stdout);
    }

    @Test
    void evaluateInlineEmitsVerboseTrace() {
        Path database = tempDir.resolve("ocra-inline.db");
        CommandHarness harness = CommandHarness.create(database);

        int exitCode = harness.execute(
                "evaluate", "--suite", SUITE, "--secret", SECRET_HEX, "--challenge", CHALLENGE, "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=ocra.evaluate.inline"), stdout);
        assertTrue(stdout.contains("metadata.suite=" + SUITE), stdout);
        assertTrue(stdout.contains("normalize.request"), stdout);
        assertTrue(stdout.contains("create.descriptor"), stdout);
        assertTrue(stdout.contains("parse.suite"), stdout);
        assertTrue(stdout.contains("normalize.inputs"), stdout);
        assertTrue(stdout.contains("assemble.message"), stdout);
        assertTrue(stdout.contains("message.sha256"), stdout);
        assertTrue(stdout.contains("compute.hmac"), stdout);
        assertTrue(stdout.contains("truncate.dynamic"), stdout);
        assertTrue(stdout.contains("mod.reduce"), stdout);
        assertEquals(
                expectedSecretHash(),
                attributeValue(stdout, "secret.hash"),
                () -> "secret.hash trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedQuestionHex(),
                attributeValue(stdout, "question.hex"),
                () -> "question.hex trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedMessageSha256(),
                attributeValue(stdout, "message.sha256"),
                () -> "message.sha256 trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedHmacHex(),
                attributeValue(stdout, "hmac.hex"),
                () -> "hmac.hex trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedCanonicalAlgorithm(),
                attributeValue(stdout, "alg"),
                () -> "alg trace attribute mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedPartsCount()),
                attributeValue(stdout, "parts.count"),
                () -> "parts.count trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedPartsOrderQuestionOnly(),
                attributeValue(stdout, "parts.order"),
                () -> "parts.order trace attribute mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedSuiteLengthBytes()),
                attributeValue(stdout, "segment.0.suite.len.bytes"),
                () -> "segment.0.suite.len.bytes mismatch\n" + stdout);
        assertEquals(
                "1",
                attributeValue(stdout, "segment.1.separator.len.bytes"),
                () -> "segment.1.separator.len.bytes mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedQuestionLengthBytes()),
                attributeValue(stdout, "segment.3.question.len.bytes"),
                () -> "segment.3.question.len.bytes mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedMessageLengthBytes()),
                attributeValue(stdout, "message.len.bytes"),
                () -> "message.len.bytes mismatch\n" + stdout);
    }

    @Test
    void verifyStoredEmitsVerboseTrace() {
        Path database = tempDir.resolve("ocra-verify-stored.db");
        CommandHarness harness = CommandHarness.create(database);
        harness.seedStoredCredential("ocra-verify");

        int exitCode = harness.execute(
                "verify",
                "--credential-id",
                "ocra-verify",
                "--otp",
                EXPECTED_OTP,
                "--challenge",
                CHALLENGE,
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=ocra.verify.stored"), stdout);
        assertTrue(stdout.contains("metadata.credentialId=ocra-verify"), stdout);
        assertTrue(stdout.contains("metadata.tier=educational"), stdout);
        assertEquals(EXPECTED_OTP, attributeValue(stdout, "compare.expected"), stdout::toString);
        assertEquals(EXPECTED_OTP, attributeValue(stdout, "compare.supplied"), stdout::toString);
        assertEquals("true", attributeValue(stdout, "compare.match"), stdout::toString);
        assertEquals(
                expectedCanonicalAlgorithm(),
                attributeValue(stdout, "alg"),
                () -> "alg trace attribute mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedPartsCount()),
                attributeValue(stdout, "parts.count"),
                () -> "parts.count trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedPartsOrderQuestionOnly(),
                attributeValue(stdout, "parts.order"),
                () -> "parts.order trace attribute mismatch\n" + stdout);
    }

    @Test
    void verifyInlineEmitsVerboseTrace() {
        Path database = tempDir.resolve("ocra-verify-inline.db");
        CommandHarness harness = CommandHarness.create(database);

        int exitCode = harness.execute(
                "verify",
                "--suite",
                SUITE,
                "--secret",
                SECRET_HEX,
                "--otp",
                EXPECTED_OTP,
                "--challenge",
                CHALLENGE,
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=ocra.verify.inline"), stdout);
        assertTrue(stdout.contains("metadata.suite=" + SUITE), stdout);
        assertTrue(stdout.contains("create.descriptor"), stdout);
        assertTrue(stdout.contains("normalize.inputs"), stdout);
        assertTrue(stdout.contains("compare.expected"), stdout);
        assertEquals(EXPECTED_OTP, attributeValue(stdout, "compare.expected"), stdout::toString);
        assertEquals(EXPECTED_OTP, attributeValue(stdout, "compare.supplied"), stdout::toString);
        assertEquals("true", attributeValue(stdout, "compare.match"), stdout::toString);
        assertEquals(
                expectedCanonicalAlgorithm(),
                attributeValue(stdout, "alg"),
                () -> "alg trace attribute mismatch\n" + stdout);
        assertEquals(
                String.valueOf(expectedPartsCount()),
                attributeValue(stdout, "parts.count"),
                () -> "parts.count trace attribute mismatch\n" + stdout);
        assertEquals(
                expectedPartsOrderQuestionOnly(),
                attributeValue(stdout, "parts.order"),
                () -> "parts.order trace attribute mismatch\n" + stdout);
    }

    private static final class CommandHarness {

        private final OcraCli cli;
        private final CommandLine commandLine;
        private final Path database;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.database = database.toAbsolutePath();
            this.cli = new OcraCli();
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

        void seedStoredCredential(String credentialId) {
            int exitCode = execute("import", "--credential-id", credentialId, "--suite", SUITE, "--secret", SECRET_HEX);
            if (exitCode != CommandLine.ExitCode.OK) {
                throw new IllegalStateException(
                        "Failed to seed OCRA credential: exitCode=" + exitCode + " stderr=" + stderr());
            }
        }
    }

    private static final HexFormat HEX = HexFormat.of();

    private static String expectedSecretHash() {
        return sha256Digest(hexBytes(SECRET_HEX));
    }

    private static String expectedQuestionHex() {
        return hexString(CHALLENGE.getBytes(StandardCharsets.US_ASCII));
    }

    private static String expectedMessageSha256() {
        return sha256Digest(messageBytes());
    }

    private static String expectedHmacHex() {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(hexBytes(SECRET_HEX), "RAW"));
            return hexString(mac.doFinal(messageBytes()));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute expected HMAC", ex);
        }
    }

    private static byte[] messageBytes() {
        byte[] suite = SUITE.getBytes(StandardCharsets.US_ASCII);
        byte[] question = hexBytes(padQuestionHex());
        byte[] payload = new byte[suite.length + 1 + question.length];
        System.arraycopy(suite, 0, payload, 0, suite.length);
        payload[suite.length] = 0x00;
        System.arraycopy(question, 0, payload, suite.length + 1, question.length);
        return payload;
    }

    private static String padQuestionHex() {
        String base = expectedQuestionHex();
        StringBuilder builder = new StringBuilder(base);
        while (builder.length() < 256) {
            builder.append('0');
        }
        return builder.toString();
    }

    private static int expectedSuiteLengthBytes() {
        return SUITE.getBytes(StandardCharsets.US_ASCII).length;
    }

    private static int expectedQuestionLengthBytes() {
        return padQuestionHex().length() / 2;
    }

    private static int expectedMessageLengthBytes() {
        return messageBytes().length;
    }

    private static String expectedCanonicalAlgorithm() {
        return "HMAC-SHA-1";
    }

    private static int expectedPartsCount() {
        return 3;
    }

    private static String expectedPartsOrderQuestionOnly() {
        return "[suite, sep, question]";
    }

    private static String computeExpectedOtp() {
        OcraCredentialFactory factory = new OcraCredentialFactory();
        var descriptor = factory.createDescriptor(new OcraCredentialRequest(
                "cli-verify",
                SUITE,
                SECRET_HEX,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "cli-verbose")));
        return OcraResponseCalculator.generate(
                descriptor, new OcraExecutionContext(null, CHALLENGE, null, null, null, null, null));
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

    private static String attributeValue(String stdout, String attribute) {
        return java.util.Arrays.stream(stdout.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith(attribute + " = "))
                .map(line -> line.substring((attribute + " = ").length()))
                .findFirst()
                .orElse(null);
    }
}
