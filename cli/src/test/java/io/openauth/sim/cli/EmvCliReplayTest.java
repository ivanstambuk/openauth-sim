package io.openauth.sim.cli;

import static io.openauth.sim.cli.EmvCliTraceAssertions.assertTraceSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures.ReplayFixture;
import io.openauth.sim.core.emv.cap.EmvCapReplayMismatchFixtures;
import io.openauth.sim.core.emv.cap.EmvCapReplayMismatchFixtures.MismatchFixture;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.json.SimpleJson;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class EmvCliReplayTest {

    @TempDir
    Path tempDir;

    @Test
    void storedReplayEmitsMatchTelemetry() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-identify-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-replay.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--credential-id",
                fixture.credentialId(),
                "--mode",
                fixture.mode().name(),
                "--otp",
                fixture.otpDecimal(),
                "--search-backward",
                String.valueOf(fixture.previewWindow().backward()),
                "--search-forward",
                String.valueOf(fixture.previewWindow().forward()),
                "--include-trace");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        String masterKeyDigest = expectedMasterKeyDigest(vector);
        assertTrue(stdout.contains("event=cli.emv.cap.replay"), stdout);
        assertTrue(stdout.contains("status=match"), stdout);
        assertTrue(stdout.contains("reasonCode=match"), stdout);
        assertTrue(stdout.contains("credentialSource=stored"), stdout);
        assertTrue(stdout.contains("matchedDelta=0"), stdout);
        assertTrue(stdout.contains("trace.masterKeySha256=" + masterKeyDigest), stdout);
    }

    @Test
    void inlineReplayMismatchReturnsMismatchStatus() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        MismatchFixture mismatch = EmvCapReplayMismatchFixtures.load("inline-sign-mismatch");
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-inline.db"));

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--challenge",
                vector.input().customerInputs().challenge(),
                "--reference",
                vector.input().customerInputs().reference(),
                "--amount",
                vector.input().customerInputs().amount(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--otp",
                fixture.mismatchOtpDecimal(),
                "--search-backward",
                String.valueOf(fixture.previewWindow().backward()),
                "--search-forward",
                String.valueOf(fixture.previewWindow().forward()),
                "--include-trace",
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout().trim();
        assertFalse(stdout.isEmpty(), "JSON output must be present");
        assertTrue(stdout.contains("\"status\":\"mismatch\""), stdout);
        assertTrue(stdout.contains("\"reasonCode\":\"otp_mismatch\""), stdout);
        assertTrue(stdout.contains("\"credentialSource\":\"inline\""), stdout);
        assertTrue(stdout.contains("\"mode\":\"" + fixture.mode().name() + "\""), stdout);

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) root.get("trace");
        assertNotNull(trace, "Trace payload should be present when includeTrace is true");
        assertTraceSchema(trace);
        String expectedOtp = (String) trace.get("expectedOtp");
        assertNotNull(expectedOtp, "expectedOtp should be present on mismatch traces");
        assertFalse(expectedOtp.isBlank(), "expectedOtp should not be blank");
        assertTrue(expectedOtp.matches("\\d+"), "expectedOtp should contain only digits");
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) trace.get("provenance");
        assertNotNull(provenance, "Provenance payload should accompany verbose traces");
        @SuppressWarnings("unchecked")
        Map<String, Object> decimalization = (Map<String, Object>) provenance.get("decimalizationOverlay");
        assertNotNull(decimalization, "Decimalization overlay should be present in provenance");
        assertEquals(decimalization.get("otp"), expectedOtp);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) root.get("metadata");
        assertNotNull(metadata, "Metadata payload should exist on CLI JSON output");
        assertEquals(
                mismatch.expectedOtpHash(),
                metadata.get("expectedOtpHash"),
                "expectedOtpHash should surface alongside mismatch metadata");
    }

    @Test
    void storedReplaySupportsInlineOverride() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-override.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--credential-id",
                fixture.credentialId(),
                "--mode",
                fixture.mode().name(),
                "--otp",
                fixture.otpDecimal(),
                "--search-backward",
                String.valueOf(fixture.previewWindow().backward()),
                "--search-forward",
                String.valueOf(fixture.previewWindow().forward()),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--challenge",
                vector.input().customerInputs().challenge(),
                "--reference",
                vector.input().customerInputs().reference(),
                "--amount",
                vector.input().customerInputs().amount(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex());

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("credentialSource=stored"), stdout);
        assertTrue(stdout.contains("matchedDelta=0"), stdout);
        assertTrue(stdout.contains("metadata.branchFactor=" + vector.input().branchFactor()), stdout);
        assertTrue(stdout.contains("metadata.height=" + vector.input().height()), stdout);
        assertTrue(stdout.contains("trace.masterKeySha256=" + expectedMasterKeyDigest(vector)), stdout);
    }

    @Test
    void inlineReplayMatchReturnsMatchStatus() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-inline-match.db"));

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--challenge",
                vector.input().customerInputs().challenge(),
                "--reference",
                vector.input().customerInputs().reference(),
                "--amount",
                vector.input().customerInputs().amount(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--otp",
                fixture.otpDecimal(),
                "--search-backward",
                String.valueOf(fixture.previewWindow().backward()),
                "--search-forward",
                String.valueOf(fixture.previewWindow().forward()),
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout().trim();
        assertFalse(stdout.isEmpty(), "JSON output must be present");
        assertTrue(stdout.contains("\"status\":\"match\""), stdout);
        assertTrue(stdout.contains("\"reasonCode\":\"match\""), stdout);
        assertTrue(stdout.contains("\"credentialSource\":\"inline\""), stdout);
    }

    @Test
    void invalidModeReturnsUsageError() {
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-invalid-mode.db"));

        int exitCode = harness.execute("cap", "replay", "--mode", "BAD", "--otp", "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("error=invalid_mode"), stderr);
        assertTrue(stderr.contains("Mode must be IDENTIFY, RESPOND, or SIGN"), stderr);
    }

    @Test
    void storedReplayUnknownCredentialFailsValidation() {
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-unknown.db"));

        int exitCode = harness.execute(
                "cap", "replay", "--credential-id", "emv-cap:missing", "--mode", "IDENTIFY", "--otp", "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("Unknown EMV/CAP credential"), stderr);
        assertTrue(stderr.contains("event=cli.emv.cap.replay"), stderr);
    }

    @Test
    void inlineReplayInvalidIccTemplateFailsValidation() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-inline-invalid-template.db"));

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--challenge",
                vector.input().customerInputs().challenge(),
                "--reference",
                vector.input().customerInputs().reference(),
                "--amount",
                vector.input().customerInputs().amount(),
                "--icc-template",
                "123",
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--otp",
                fixture.otpDecimal());

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("iccTemplate must contain an even number of characters"), stderr);
        assertTrue(stderr.contains("event=cli.emv.cap.replay"), stderr);
    }

    @Test
    void storedReplayRejectsNegativeSearchWindow() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-negative-window.db"));

        int seedExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, seedExit, harness.stderr());

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--credential-id",
                fixture.credentialId(),
                "--mode",
                fixture.mode().name(),
                "--otp",
                fixture.otpDecimal(),
                "--search-backward",
                "-1");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("driftBackward must be non-negative"), stderr);
    }

    @Test
    void inlineReplayMissingMasterKeyFailsValidation() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-identify-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
        CommandHarness harness = CommandHarness.create(tempDir.resolve("emv-cap-missing-master.db"));

        int exitCode = harness.execute(
                "cap",
                "replay",
                "--mode",
                vector.input().mode().name(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--otp",
                fixture.otpDecimal());

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.emv.cap.replay"), stderr);
        assertTrue(stderr.contains("status=invalid"), stderr);
        assertTrue(stderr.contains("error=masterKey must be provided"), stderr);
    }

    private static String expectedMasterKeyDigest(EmvCapVector vector) {
        return sha256Digest(vector.input().masterKeyHex());
    }

    private static String sha256Digest(String hex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = hexToBytes(hex);
            byte[] hashed = digest.digest(bytes);
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.trim().toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex input must contain an even number of characters");
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
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
