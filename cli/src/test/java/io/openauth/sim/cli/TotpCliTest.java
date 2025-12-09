package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class TotpCliTest {

    private static final String CREDENTIAL_ID = "totp-demo";
    private static final TotpJsonVector STORED_VECTOR =
            TotpJsonVectorFixtures.getById("rfc6238_sha1_digits6_t1111111111");
    private static final SecretMaterial INLINE_SECRET =
            SecretMaterial.fromStringUtf8("1234567890123456789012345678901234567890123456789012345678901234");
    private static final String INLINE_SECRET_BASE32 =
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA=";

    @TempDir
    Path tempDir;

    @Test
    void listShowsTotpCredentialsWithMetadata() throws Exception {
        Path database = tempDir.resolve("totp.db");
        CommandHarness harness = CommandHarness.create(database);

        TotpDescriptor descriptor = TotpDescriptor.create(
                CREDENTIAL_ID,
                STORED_VECTOR.secret(),
                STORED_VECTOR.algorithm(),
                STORED_VECTOR.digits(),
                STORED_VECTOR.stepDuration(),
                TotpDriftWindow.of(STORED_VECTOR.driftBackwardSteps(), STORED_VECTOR.driftForwardSteps()));
        harness.save(descriptor);

        int exitCode = harness.execute("list");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.totp.list status=success count=1"));
        assertTrue(stdout.contains("credentialId=" + CREDENTIAL_ID));
        assertTrue(stdout.toLowerCase().contains("algorithm=sha1"));
        assertTrue(stdout.contains("stepSeconds=30"));
        assertTrue(stdout.contains("driftBackwardSteps=1 driftForwardSteps=1"));
    }

    @Test
    void evaluateStoredCredentialGeneratesOtpWithinDriftWindow() throws Exception {
        Path database = tempDir.resolve("totp.db");
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
                "1",
                "--window-forward",
                "1");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.totp.evaluate status=success"));
        assertTrue(stdout.contains("reasonCode=generated"));
        assertTrue(stdout.contains("credentialReference=true"));
        assertTrue(stdout.contains("matchedSkewSteps=0"));
        assertTrue(stdout.contains("otp="), "generated OTP should be printed in the evaluation frame");
        assertTrue(stdout.contains("Preview window:"), () -> "stdout:\n" + stdout);
        assertTrue(stdout.contains("[0]"), "preview output should highlight delta 0");
        assertTrue(stdout.contains("+1"), "preview output should include forward delta");
        assertTrue(stdout.contains("-1"), "preview output should include backward delta");

        // Store should remain unchanged (no mutation for TOTP validation).
        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            Credential credential = store.findByName(CREDENTIAL_ID).orElseThrow();
            assertEquals(CredentialType.OATH_TOTP, credential.type());
        }
    }

    @Test
    void evaluateInlineGeneratesOtp() throws Exception {
        Path database = tempDir.resolve("totp.db");
        CommandHarness harness = CommandHarness.create(database);

        TotpDescriptor inlineDescriptor = TotpDescriptor.create(
                "inline", INLINE_SECRET, TotpHashAlgorithm.SHA512, 8, Duration.ofSeconds(60), TotpDriftWindow.of(0, 0));
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
                "--timestamp",
                Long.toString(issuedAt.getEpochSecond()));

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.totp.evaluate status=success"));
        assertTrue(stdout.contains("credentialReference=false"));
        assertTrue(stdout.contains("reasonCode=generated"));
        assertTrue(stdout.contains("otp="));
        assertTrue(stdout.contains("Preview window:"), () -> "stdout:\n" + stdout);
        assertTrue(stdout.contains("[0]"), () -> "stdout:\n" + stdout);

        String expectedOtp = TotpGenerator.generate(inlineDescriptor, issuedAt);
        String actualOtp = extractOtp(stdout);
        assertEquals(expectedOtp, actualOtp, "CLI should emit the generated OTP");
    }

    @Test
    void evaluateInlineSupportsBase32Secrets() throws Exception {
        Path database = tempDir.resolve("totp-base32.db");
        CommandHarness harness = CommandHarness.create(database);

        TotpDescriptor inlineDescriptor = TotpDescriptor.create(
                "inline", INLINE_SECRET, TotpHashAlgorithm.SHA512, 8, Duration.ofSeconds(60), TotpDriftWindow.of(0, 0));
        Instant issuedAt = Instant.ofEpochSecond(1_234_567_890L);

        int exitCode = harness.execute(
                "evaluate",
                "--secret-base32",
                INLINE_SECRET_BASE32,
                "--algorithm",
                "SHA512",
                "--digits",
                "8",
                "--step-seconds",
                "60",
                "--timestamp",
                Long.toString(issuedAt.getEpochSecond()));

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.totp.evaluate status=success"));
        assertTrue(stdout.contains("credentialReference=false"));
        String expectedOtp = TotpGenerator.generate(inlineDescriptor, issuedAt);
        assertEquals(expectedOtp, extractOtp(stdout));
        assertTrue(stdout.contains("Preview window:"), () -> "stdout:\n" + stdout);
        assertTrue(stdout.contains("[0]"), () -> "stdout:\n" + stdout);
    }

    @Test
    void evaluateInlineRejectsHexAndBase32Combination() {
        Path database = tempDir.resolve("totp-conflict.db");
        CommandHarness harness = CommandHarness.create(database);

        int exitCode = harness.execute(
                "evaluate",
                "--secret",
                INLINE_SECRET.asHex(),
                "--secret-base32",
                INLINE_SECRET_BASE32,
                "--algorithm",
                "SHA1",
                "--digits",
                "6",
                "--step-seconds",
                "30");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.totp.evaluate status=invalid"));
        assertTrue(
                stderr.contains("Provide either --secret or --secret-base32"),
                () -> "stderr did not include exclusivity hint:\n" + stderr);
    }

    private static final class CommandHarness {

        private final TotpCli cli;
        private final CommandLine commandLine;
        private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();
        private final Path database;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.database = database;
            this.cli = new TotpCli();
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

        void save(TotpDescriptor descriptor) throws Exception {
            try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
                Credential credential = VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
                store.save(credential);
            }
        }
    }

    private static String extractOtp(String stdout) {
        int otpIndex = stdout.indexOf("otp=");
        assertTrue(otpIndex >= 0, "OTP not found in CLI output:\n" + stdout);
        int endIndex = stdout.length();
        int spaceIndex = stdout.indexOf(' ', otpIndex);
        if (spaceIndex != -1) {
            endIndex = Math.min(endIndex, spaceIndex);
        }
        int newlineIndex = stdout.indexOf('\n', otpIndex);
        if (newlineIndex != -1) {
            endIndex = Math.min(endIndex, newlineIndex);
        }
        int carriageReturnIndex = stdout.indexOf('\r', otpIndex);
        if (carriageReturnIndex != -1) {
            endIndex = Math.min(endIndex, carriageReturnIndex);
        }
        return stdout.substring(otpIndex + 4, endIndex).trim();
    }
}
