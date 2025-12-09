package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures.TotpJsonVector;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class TotpCliVerboseTraceTest {

    private static final String CREDENTIAL_ID = "totp-trace-demo";
    private static final TotpJsonVector STORED_VECTOR =
            TotpJsonVectorFixtures.getById("rfc6238_sha1_digits6_t1111111111");
    private static final SecretMaterial INLINE_SECRET =
            SecretMaterial.fromStringUtf8("1234567890123456789012345678901234567890123456789012345678901234");

    @TempDir
    Path tempDir;

    @Test
    void evaluateStoredEmitsVerboseTrace() throws Exception {
        Path database = tempDir.resolve("totp-trace.db");
        CommandHarness harness = CommandHarness.create(database);

        TotpDescriptor descriptor = TotpDescriptor.create(
                CREDENTIAL_ID,
                STORED_VECTOR.secret(),
                STORED_VECTOR.algorithm(),
                STORED_VECTOR.digits(),
                STORED_VECTOR.stepDuration(),
                TotpDriftWindow.of(STORED_VECTOR.driftBackwardSteps(), STORED_VECTOR.driftForwardSteps()));
        harness.save(descriptor);

        Instant timestamp = STORED_VECTOR.timestamp();

        int exitCode = harness.execute(
                "evaluate",
                "--credential-id",
                CREDENTIAL_ID,
                "--timestamp",
                Long.toString(timestamp.getEpochSecond()),
                "--window-backward",
                Integer.toString(STORED_VECTOR.driftBackwardSteps()),
                "--window-forward",
                Integer.toString(STORED_VECTOR.driftForwardSteps()),
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=totp.evaluate.stored"), stdout);
        assertTrue(stdout.contains("metadata.mode=stored"), stdout);
        assertTrue(stdout.contains("metadata.tier=educational"), stdout);
        assertTrue(stdout.contains("metadata.credentialId=" + CREDENTIAL_ID), stdout);
        assertTrue(stdout.contains("derive.time-counter"), stdout);
        assertTrue(stdout.contains("mod.reduce"), stdout);
        String expectedSecretHash = sha256Digest(STORED_VECTOR.secret().value());
        assertTrue(stdout.contains("  secret.hash = " + expectedSecretHash), stdout);
        long counter = timestamp.getEpochSecond() / STORED_VECTOR.stepDuration().getSeconds();
        assertTrue(stdout.contains("  time.counter.int = " + counter), stdout);
        assertTrue(stdout.contains("  time.counter.hex = " + String.format("%016x", counter)), stdout);
        assertTrue(stdout.contains("=== End Verbose Trace ==="), stdout);
    }

    @Test
    void evaluateStoredOmitsVerboseTraceWhenDisabled() throws Exception {
        Path database = tempDir.resolve("totp-default.db");
        CommandHarness harness = CommandHarness.create(database);

        TotpDescriptor descriptor = TotpDescriptor.create(
                CREDENTIAL_ID,
                STORED_VECTOR.secret(),
                STORED_VECTOR.algorithm(),
                STORED_VECTOR.digits(),
                STORED_VECTOR.stepDuration(),
                TotpDriftWindow.of(STORED_VECTOR.driftBackwardSteps(), STORED_VECTOR.driftForwardSteps()));
        harness.save(descriptor);

        Instant timestamp = STORED_VECTOR.timestamp();

        int exitCode = harness.execute(
                "evaluate",
                "--credential-id",
                CREDENTIAL_ID,
                "--timestamp",
                Long.toString(timestamp.getEpochSecond()),
                "--window-backward",
                Integer.toString(STORED_VECTOR.driftBackwardSteps()),
                "--window-forward",
                Integer.toString(STORED_VECTOR.driftForwardSteps()));

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertFalse(stdout.contains("=== Verbose Trace ==="), stdout);
        assertFalse(stdout.contains("operation=totp.evaluate.stored"), stdout);
    }

    @Test
    void evaluateInlineEmitsVerboseTrace() {
        Path database = tempDir.resolve("totp-inline-trace.db");
        CommandHarness harness = CommandHarness.create(database);

        Instant issuedAt = Instant.ofEpochSecond(1_234_567_890L);

        int exitCode = harness.execute(
                "evaluate",
                "--secret",
                INLINE_SECRET.asHex(),
                "--algorithm",
                "SHA512",
                "--digits",
                "8",
                "--step-seconds",
                "60",
                "--window-backward",
                "0",
                "--window-forward",
                "0",
                "--timestamp",
                Long.toString(issuedAt.getEpochSecond()),
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=totp.evaluate.inline"), stdout);
        assertTrue(stdout.contains("metadata.mode=inline"), stdout);
        assertTrue(stdout.contains("normalize.input"), stdout);
        assertTrue(stdout.contains("derive.time-counter"), stdout);
        assertTrue(stdout.contains("mod.reduce"), stdout);
        String inlineSecretHash = sha256Digest(INLINE_SECRET.value());
        assertTrue(stdout.contains("  secret.hash = " + inlineSecretHash), stdout);

        String expectedOtp = TotpGenerator.generate(
                TotpDescriptor.create(
                        "inline",
                        INLINE_SECRET,
                        TotpHashAlgorithm.SHA512,
                        8,
                        Duration.ofSeconds(60),
                        TotpDriftWindow.of(0, 0)),
                issuedAt);
        assertTrue(stdout.contains("otp=" + expectedOtp), stdout);
    }

    private static final class CommandHarness {

        private final TotpCli cli;
        private final CommandLine commandLine;
        private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();
        private final Path database;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.database = database.toAbsolutePath();
            this.cli = new TotpCli();
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

        void save(TotpDescriptor descriptor) throws Exception {
            try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
                Credential credential = VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
                store.save(credential);
            }
        }
    }

    private static String sha256Digest(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + toHex(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
