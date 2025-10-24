package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures.HotpJsonVector;
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
        assertTrue(stdout.contains("  output.otp = "), stdout);
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
}
