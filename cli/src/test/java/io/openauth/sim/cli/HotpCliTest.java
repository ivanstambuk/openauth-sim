package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures.HotpJsonVector;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class HotpCliTest {

    private static final String CREDENTIAL_ID = "hotp-demo";

    @TempDir
    Path tempDir;

    @Test
    void importListEvaluateStoredCredentialFlow() throws Exception {
        Path databasePath = databasePath();

        importCredential(databasePath);

        CommandHarness listHarness = harness(databasePath);
        int listExit = listHarness.execute("list");
        assertEquals(CommandLine.ExitCode.OK, listExit, listHarness.stderr());
        String listOutput = listHarness.stdout();
        assertTrue(listOutput.contains("credentialId=" + CREDENTIAL_ID));
        assertTrue(listOutput.toLowerCase(Locale.ROOT).contains("sha1"));

        CommandHarness evaluateHarness = harness(databasePath);
        int evaluateExit = evaluateHarness.execute("evaluate", "--credential-id", CREDENTIAL_ID);
        assertEquals(CommandLine.ExitCode.OK, evaluateExit, evaluateHarness.stderr());
        String evaluateOutput = evaluateHarness.stdout();
        assertTrue(evaluateOutput.contains("event=cli.hotp.evaluate"));
        assertTrue(evaluateOutput.contains("previousCounter=0"));
        assertTrue(evaluateOutput.contains("nextCounter=1"));
        assertTrue(evaluateOutput.contains("generatedOtp=" + otpForCounter(0)));

        try (CredentialStore store = CredentialStoreFactory.openFileStore(databasePath)) {
            Credential credential = store.findByName(CREDENTIAL_ID).orElseThrow();
            assertEquals(CredentialType.OATH_HOTP, credential.type());
            assertEquals("1", credential.attributes().get("hotp.counter"));
        }
    }

    @Test
    void importFailsForUnknownAlgorithm() throws Exception {
        Path databasePath = databasePath();
        CommandHarness harness = harness(databasePath);
        HotpJsonVector sample = vector(0L);

        int exitCode = harness.execute(
                "import",
                "--credential-id",
                CREDENTIAL_ID,
                "--secret",
                sample.secret().asHex(),
                "--digits",
                String.valueOf(sample.digits()),
                "--counter",
                String.valueOf(sample.counter()),
                "--algorithm",
                "sha999");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.hotp.issue"));
        assertTrue(stderr.contains("status=invalid"));

        try (CredentialStore store = CredentialStoreFactory.openFileStore(databasePath)) {
            assertFalse(store.findByName(CREDENTIAL_ID).isPresent());
        }
    }

    @Test
    void listReportsZeroCredentialsWhenStoreEmpty() {
        Path databasePath = databasePath();
        CommandHarness harness = harness(databasePath);

        int exitCode = harness.execute("list");
        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String output = harness.stdout();
        assertTrue(output.contains("event=cli.hotp.list"));
        assertTrue(output.contains("count=0"));
    }

    @Test
    void evaluateReportsMissingCredential() {
        Path databasePath = databasePath();
        CommandHarness harness = harness(databasePath);

        int exitCode = harness.execute("evaluate", "--credential-id", CREDENTIAL_ID);

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.hotp.evaluate"));
        assertTrue(stderr.contains("reasonCode=credential_not_found"));
    }

    @Test
    void listFailsWhenDatabasePathCannotResolve() {
        Path failing = TestPaths.failingAbsolutePath(tempDir.resolve("list.db"), new IllegalStateException());
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(failing);

        int exitCode = harness.execute("list");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.hotp.list"));
        assertTrue(stderr.contains("status=error"));
    }

    @Test
    void evaluateFailsWhenDatabasePathCannotResolve() {
        Path failing = TestPaths.failingAbsolutePath(tempDir.resolve("evaluate.db"), new IllegalStateException("boom"));
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(failing);

        int exitCode = harness.execute("evaluate", "--credential-id", CREDENTIAL_ID);

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.hotp.evaluate"));
        assertTrue(stderr.contains("status=error"));
    }

    @Test
    void importPersistsMetadataAttributes() throws Exception {
        Path databasePath = databasePath();
        CommandHarness harness = harness(databasePath);
        HotpJsonVector sample = vector(0L);

        int exitCode = harness.execute(
                "import",
                "--credential-id",
                CREDENTIAL_ID,
                "--secret",
                sample.secret().asHex(),
                "--digits",
                String.valueOf(sample.digits()),
                "--counter",
                String.valueOf(sample.counter()),
                "--algorithm",
                sample.algorithm().name(),
                "--metadata",
                "label=primary,team=dev");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());

        try (CredentialStore store = CredentialStoreFactory.openFileStore(databasePath)) {
            Credential credential = store.findByName(CREDENTIAL_ID).orElseThrow();
            assertEquals("primary", credential.attributes().get("hotp.metadata.label"));
            assertEquals("dev", credential.attributes().get("hotp.metadata.team"));
        }
    }

    @Test
    void importReportsUnexpectedErrorWhenDatabaseResolutionFails() {
        Path failing = TestPaths.failingAbsolutePath(tempDir.resolve("broken.db"), new IllegalStateException("boom"));
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(failing);
        HotpJsonVector sample = vector(0L);

        int exitCode = harness.execute(
                "import",
                "--credential-id",
                CREDENTIAL_ID,
                "--secret",
                sample.secret().asHex(),
                "--digits",
                String.valueOf(sample.digits()),
                "--counter",
                String.valueOf(sample.counter()),
                "--algorithm",
                sample.algorithm().name());

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.hotp.issue"));
        assertTrue(stderr.contains("status=error"));
        assertTrue(stderr.contains("reasonCode=unexpected_error"));
    }

    @Test
    void importRejectsTypeMismatch() throws Exception {
        Path databasePath = databasePath();
        HotpJsonVector sample = vector(0L);
        try (CredentialStore store = CredentialStoreFactory.openFileStore(databasePath)) {
            Credential existing = Credential.create(CREDENTIAL_ID, CredentialType.OATH_OCRA, sample.secret(), Map.of());
            store.save(existing);
        }

        CommandHarness harness = harness(databasePath);
        int exitCode = harness.execute(
                "import",
                "--credential-id",
                CREDENTIAL_ID,
                "--secret",
                sample.secret().asHex(),
                "--digits",
                String.valueOf(sample.digits()),
                "--counter",
                String.valueOf(sample.counter()),
                "--algorithm",
                sample.algorithm().name());

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.hotp.issue"));
        assertTrue(stderr.contains("reasonCode=type_mismatch"));
    }

    @Test
    void sanitizeMessageHandlesNullAndWhitespace() {
        assertEquals("unspecified", HotpCli.sanitizeMessage(null));
        assertEquals("boom", HotpCli.sanitizeMessage("  boom\n"));
    }

    @Test
    void listUsesOverriddenDatabasePath() throws Exception {
        Path custom = tempDir.resolve("overridden.db");
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(custom);

        int exitCode = harness.execute("list");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        assertTrue(harness.stdout().contains("event=cli.hotp.list"));

        Path resolved = harness.cli().databasePath();
        assertEquals(custom.toAbsolutePath(), resolved);
    }

    @Test
    void evaluateReportsCounterOverflow() throws Exception {
        Path databasePath = databasePath();

        CommandHarness importHarness = harness(databasePath);
        HotpJsonVector sample = vector(0L);
        int importExit = importHarness.execute(
                "import",
                "--credential-id",
                CREDENTIAL_ID,
                "--secret",
                sample.secret().asHex(),
                "--digits",
                String.valueOf(sample.digits()),
                "--counter",
                Long.toString(Long.MAX_VALUE),
                "--algorithm",
                sample.algorithm().name());
        assertEquals(CommandLine.ExitCode.OK, importExit, importHarness.stderr());

        CommandHarness evaluateHarness = harness(databasePath);
        int exitCode = evaluateHarness.execute("evaluate", "--credential-id", CREDENTIAL_ID);

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = evaluateHarness.stderr();
        assertTrue(stderr.contains("status=invalid"));
        assertTrue(stderr.contains("reasonCode=counter_overflow"));
    }

    private CommandHarness harness(Path databasePath) {
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(databasePath);
        return harness;
    }

    private Path databasePath() {
        return tempDir.resolve("credentials.db");
    }

    private void importCredential(Path databasePath) {
        HotpJsonVector sample = vector(0L);
        CommandHarness harness = harness(databasePath);
        int exitCode = harness.execute(
                "import",
                "--credential-id",
                CREDENTIAL_ID,
                "--secret",
                sample.secret().asHex(),
                "--digits",
                String.valueOf(sample.digits()),
                "--counter",
                String.valueOf(sample.counter()),
                "--algorithm",
                sample.algorithm().name());

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String output = harness.stdout();
        assertTrue(output.contains("event=cli.hotp.issue"));
        assertTrue(output.contains("credentialId=" + CREDENTIAL_ID));
    }

    private static String otpForCounter(long counter) {
        return vector(counter).otp();
    }

    private static HotpJsonVector vector(long counter) {
        return HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == 6 && v.counter() == counter)
                .findFirst()
                .orElseThrow();
    }

    private static final class CommandHarness {

        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final CommandLine commandLine;

        private CommandHarness() {
            commandLine = new CommandLine(new HotpCli());
            commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        }

        static CommandHarness create() {
            return new CommandHarness();
        }

        HotpCli cli() {
            return commandLine.getCommand();
        }

        int execute(String... args) {
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
