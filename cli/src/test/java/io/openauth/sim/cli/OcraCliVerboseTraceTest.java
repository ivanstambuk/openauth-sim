package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class OcraCliVerboseTraceTest {

    private static final String SUITE = "OCRA-1:HOTP-SHA1-6:QA08";
    private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
    private static final String CHALLENGE = "12345678";

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
        assertTrue(stdout.contains("normalize.request"), stdout);
        assertTrue(stdout.contains("resolve.credential"), stdout);
        assertTrue(stdout.contains("generate.otp"), stdout);
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
        assertTrue(stdout.toLowerCase(Locale.ROOT).contains("generate.otp"), stdout);
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
}
