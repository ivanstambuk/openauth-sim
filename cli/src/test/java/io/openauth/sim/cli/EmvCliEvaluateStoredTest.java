package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.Outputs;
import io.openauth.sim.core.json.SimpleJson;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class EmvCliEvaluateStoredTest {

    @TempDir
    Path tempDir;

    @Test
    void evaluateStoredCredentialEmitsOtpAndTrace() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-stored.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        int exitCode = harness.execute("cap", "evaluate-stored", "--credential-id", "emv-cap-identify-baseline");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.emv.cap.identify status=success"), stdout);
        assertTrue(stdout.contains("credentialSource=stored"), stdout);
        assertTrue(stdout.contains("otp=" + vector.outputs().otpDecimal()), stdout);
        assertTrue(stdout.contains("maskLength=" + countMaskedDigits(vector.outputs())), stdout);
        assertTrue(stdout.contains("trace.masterKeySha256=" + expectedMasterKeyDigest(vector)), stdout);
    }

    @Test
    void evaluateStoredSupportsInlineOverride() {
        EmvCapVector vector = EmvCapVectorFixtures.load("respond-baseline");
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-override.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        String overrideAtc = "00B5";
        int exitCode = harness.execute(
                "cap",
                "evaluate-stored",
                "--credential-id",
                "emv-cap-respond-baseline",
                "--atc",
                overrideAtc,
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout().trim();
        assertFalse(stdout.isEmpty(), "JSON output should be emitted");

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;

        assertNotNull(root.get("otp"), "OTP field should be present");
        @SuppressWarnings("unchecked")
        Map<String, Object> telemetry = (Map<String, Object>) root.get("telemetry");
        assertNotNull(telemetry, "Telemetry payload should be present");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) telemetry.get("fields");
        assertEquals("stored", fields.get("credentialSource"));
        assertEquals("emv-cap-respond-baseline", fields.get("credentialId"));
        assertEquals(overrideAtc, fields.get("atc"));
    }

    @Test
    void telemetryRemainsSanitizedForStoredEvaluation() {
        EmvCapVector vector = EmvCapVectorFixtures.load("sign-baseline");
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-sanitized.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        int exitCode = harness.execute("cap", "evaluate-stored", "--credential-id", "emv-cap-sign-baseline");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("sanitized=true"), stdout);
        assertTrue(stdout.contains("credentialSource=stored"), stdout);
        assertFalse(stdout.contains(vector.input().masterKeyHex()), stdout);
    }

    @Test
    void includeTraceToggleSuppressesTraceOutput() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-trace.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        int exitCode = harness.execute(
                "cap", "evaluate-stored", "--credential-id", "emv-cap-identify-baseline", "--include-trace", "false");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("otp=" + vector.outputs().otpDecimal()), stdout);
        assertFalse(stdout.contains("trace.masterKeySha256"), stdout);
    }

    private static int countMaskedDigits(Outputs outputs) {
        int count = 0;
        for (int index = 0; index < outputs.maskedDigitsOverlay().length(); index++) {
            if (outputs.maskedDigitsOverlay().charAt(index) != '.') {
                count++;
            }
        }
        return count;
    }

    private static String expectedMasterKeyDigest(EmvCapVector vector) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hexToBytes(vector.input().masterKeyHex()));
            return "sha256:" + toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }

    private static final class CommandHarness {

        private final EmvCli cli;
        private final CommandLine commandLine;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.cli = new EmvCli();
            cli.overrideDatabase(database);
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
    }
}
