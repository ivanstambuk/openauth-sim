package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures.ReplayFixture;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
        assertTrue(stdout.contains("event=cli.emv.cap.replay"), stdout);
        assertTrue(stdout.contains("status=match"), stdout);
        assertTrue(stdout.contains("reasonCode=match"), stdout);
        assertTrue(stdout.contains("credentialSource=stored"), stdout);
        assertTrue(stdout.contains("matchedDelta=0"), stdout);
    }

    @Test
    void inlineReplayMismatchReturnsMismatchStatus() {
        ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());
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
