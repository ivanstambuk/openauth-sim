package io.openauth.sim.cli;

import static io.openauth.sim.cli.TelemetryOutputAssertions.telemetryLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationReason;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraOneWayVector;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class OcraCliTest {

    private static final OcraOneWayVector INLINE_VECTOR =
            OcraJsonVectorFixtures.getOneWay("rfc6287_standard-challenge-question-repeated-digits");
    private static final OcraOneWayVector STORED_VECTOR =
            OcraJsonVectorFixtures.getOneWay("rfc6287_counter-with-hashed-pin-c-0");

    private static final String DEFAULT_SECRET_HEX =
            INLINE_VECTOR.secret().asHex().toUpperCase(Locale.ROOT);
    private static final String DEFAULT_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String DEFAULT_SUITE = INLINE_VECTOR.suite();
    private static final String VERIFY_SUITE_COUNTER = STORED_VECTOR.suite();
    private static final String VERIFY_SECRET_HEX_COUNTER =
            STORED_VECTOR.secret().asHex().toUpperCase(Locale.ROOT);
    private static final String VERIFY_PIN_HASH_SHA1 =
            STORED_VECTOR.pinHashHex().orElseThrow(() -> new IllegalStateException("Counter vector missing PIN hash"));
    private static final long VERIFY_COUNTER = STORED_VECTOR
            .counter()
            .orElseThrow(() -> new IllegalStateException("Counter vector missing counter value"));
    private static final String VERIFY_CHALLENGE_NUMERIC = STORED_VECTOR
            .challengeQuestion()
            .orElseThrow(() -> new IllegalStateException("Counter vector missing question"));

    @Test
    @DisplayName("verify command matches stored credential when context is complete")
    void verifyStoredCredentialMatch() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-verify-stored");
        Path database = tempDir.resolve("store.db");

        String credentialId = "verify-stored";
        OcraCredentialDescriptor descriptor = new OcraCredentialFactory()
                .createDescriptor(new OcraCredentialRequest(
                        credentialId,
                        VERIFY_SUITE_COUNTER,
                        VERIFY_SECRET_HEX_COUNTER,
                        SecretEncoding.HEX,
                        VERIFY_COUNTER,
                        VERIFY_PIN_HASH_SHA1,
                        null,
                        Map.of("source", "cli-verify")));
        persistDescriptor(database, descriptor);

        String expectedOtp = OcraResponseCalculator.generate(
                descriptor,
                new OcraExecutionContext(
                        VERIFY_COUNTER, VERIFY_CHALLENGE_NUMERIC, null, null, null, VERIFY_PIN_HASH_SHA1, null));

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database",
                database.toAbsolutePath().toString(),
                "verify",
                "--credential-id",
                credentialId,
                "--otp",
                expectedOtp,
                "--challenge",
                VERIFY_CHALLENGE_NUMERIC,
                "--counter",
                String.valueOf(VERIFY_COUNTER),
                "--pin-hash",
                VERIFY_PIN_HASH_SHA1);

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String output = harness.stdout() + harness.stderr();
        Map<String, String> telemetry = telemetryLine(output, "cli.ocra.verify");
        assertEquals("match", telemetry.get("status"));
        assertEquals("match", telemetry.get("reasonCode"));
        assertEquals("stored", telemetry.get("credentialSource"));
        assertEquals("true", telemetry.get("sanitized"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("verify command matches inline credential requests")
    void verifyInlineCredentialMatch() {
        OcraCredentialDescriptor descriptor = new OcraCredentialFactory()
                .createDescriptor(new OcraCredentialRequest(
                        "inline-verify",
                        DEFAULT_SUITE,
                        DEFAULT_SECRET_HEX,
                        SecretEncoding.HEX,
                        null,
                        null,
                        null,
                        Map.of("source", "cli-inline")));

        String expectedOtp = OcraResponseCalculator.generate(
                descriptor, new OcraExecutionContext(null, VERIFY_CHALLENGE_NUMERIC, null, null, null, null, null));

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "verify",
                "--suite",
                DEFAULT_SUITE,
                "--secret",
                DEFAULT_SECRET_HEX,
                "--otp",
                expectedOtp,
                "--challenge",
                VERIFY_CHALLENGE_NUMERIC);

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String output = harness.stdout() + harness.stderr();
        Map<String, String> telemetry = telemetryLine(output, "cli.ocra.verify");
        assertEquals("match", telemetry.get("status"));
        assertEquals("match", telemetry.get("reasonCode"));
        assertEquals("inline", telemetry.get("credentialSource"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("verify command accepts inline Base32 shared secrets")
    void verifyInlineCredentialMatchWithBase32() {
        OcraCredentialDescriptor descriptor = new OcraCredentialFactory()
                .createDescriptor(new OcraCredentialRequest(
                        "inline-verify-base32",
                        DEFAULT_SUITE,
                        DEFAULT_SECRET_HEX,
                        SecretEncoding.HEX,
                        null,
                        null,
                        null,
                        Map.of("source", "cli-inline")));

        String expectedOtp = OcraResponseCalculator.generate(
                descriptor, new OcraExecutionContext(null, VERIFY_CHALLENGE_NUMERIC, null, null, null, null, null));

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "verify",
                "--suite",
                DEFAULT_SUITE,
                "--secret-base32",
                DEFAULT_SECRET_BASE32,
                "--otp",
                expectedOtp,
                "--challenge",
                VERIFY_CHALLENGE_NUMERIC);

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String output = harness.stdout() + harness.stderr();
        Map<String, String> telemetry = telemetryLine(output, "cli.ocra.verify");
        assertEquals("match", telemetry.get("status"));
        assertEquals("match", telemetry.get("reasonCode"));
        assertEquals("inline", telemetry.get("credentialSource"));
    }

    @Test
    @DisplayName("verify command rejects mixed hex and Base32 secrets for inline mode")
    void verifyInlineCredentialRejectsSecretConflicts() {
        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "verify",
                "--suite",
                DEFAULT_SUITE,
                "--secret",
                DEFAULT_SECRET_HEX,
                "--secret-base32",
                DEFAULT_SECRET_BASE32,
                "--otp",
                "12345678",
                "--challenge",
                VERIFY_CHALLENGE_NUMERIC);

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.ocra.verify"));
        assertTrue(
                stderr.contains("Provide either --secret or --secret-base32"),
                () -> "stderr missing exclusivity hint:\n" + stderr);
    }

    @Test
    @DisplayName("verify command returns strict mismatch when counter differs")
    void verifyStoredCredentialStrictMismatch() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-verify-mismatch");
        Path database = tempDir.resolve("store.db");

        String credentialId = "verify-mismatch";
        OcraCredentialDescriptor descriptor = new OcraCredentialFactory()
                .createDescriptor(new OcraCredentialRequest(
                        credentialId,
                        VERIFY_SUITE_COUNTER,
                        VERIFY_SECRET_HEX_COUNTER,
                        SecretEncoding.HEX,
                        VERIFY_COUNTER,
                        VERIFY_PIN_HASH_SHA1,
                        null,
                        Map.of("source", "cli-verify")));
        persistDescriptor(database, descriptor);

        String baselineOtp = OcraResponseCalculator.generate(
                descriptor,
                new OcraExecutionContext(
                        VERIFY_COUNTER, VERIFY_CHALLENGE_NUMERIC, null, null, null, VERIFY_PIN_HASH_SHA1, null));

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database",
                database.toAbsolutePath().toString(),
                "verify",
                "--credential-id",
                credentialId,
                "--otp",
                baselineOtp,
                "--challenge",
                VERIFY_CHALLENGE_NUMERIC,
                "--counter",
                String.valueOf(VERIFY_COUNTER + 1),
                "--pin-hash",
                VERIFY_PIN_HASH_SHA1);

        assertEquals(2, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stdout() + harness.stderr(), "cli.ocra.verify");
        assertEquals("mismatch", telemetry.get("status"));
        assertEquals("strict_mismatch", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("verify command requires challenge context when suite expects it")
    void verifyRequiresChallengeContext() {
        OcraCredentialDescriptor descriptor = new OcraCredentialFactory()
                .createDescriptor(new OcraCredentialRequest(
                        "inline-verify-missing-context",
                        DEFAULT_SUITE,
                        DEFAULT_SECRET_HEX,
                        SecretEncoding.HEX,
                        null,
                        null,
                        null,
                        Map.of("source", "cli-inline")));

        String expectedOtp = OcraResponseCalculator.generate(
                descriptor, new OcraExecutionContext(null, VERIFY_CHALLENGE_NUMERIC, null, null, null, null, null));

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "verify", "--suite", DEFAULT_SUITE, "--secret", DEFAULT_SECRET_HEX, "--otp", expectedOtp);

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stdout() + harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("challenge_required", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("verify command requires OTP value")
    void verifyRequiresOtp() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute("verify", "--suite", DEFAULT_SUITE, "--secret", DEFAULT_SECRET_HEX);

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("otp_missing", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("verify command rejects providing both credential id and inline secret")
    void verifyRejectsCredentialConflict() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "verify",
                "--credential-id",
                "alpha",
                "--suite",
                DEFAULT_SUITE,
                "--secret",
                DEFAULT_SECRET_HEX,
                "--otp",
                "123456",
                "--challenge",
                "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("credential_conflict", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command reports credential_not_found when stored credential missing")
    void verifyStoredCredentialNotFound() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-verify-missing");
        Path database = tempDir.resolve("store.db");

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database",
                database.toAbsolutePath().toString(),
                "verify",
                "--credential-id",
                "missing",
                "--otp",
                "000000",
                "--challenge",
                "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("credential_not_found", telemetry.get("reasonCode"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("verify command requires credential id or inline secret")
    void verifyRequiresCredentialOrSecret() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute("verify", "--otp", "123456");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("credential_missing", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command reports mismatch for inline credential OTP")
    void verifyInlineStrictMismatch() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "verify",
                "--suite",
                DEFAULT_SUITE,
                "--secret",
                DEFAULT_SECRET_HEX,
                "--otp",
                "999999",
                "--challenge",
                "12345678");

        assertEquals(2, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stdout() + harness.stderr(), "cli.ocra.verify");
        assertEquals("mismatch", telemetry.get("status"));
        assertEquals("inline", telemetry.get("credentialSource"));
        assertEquals("strict_mismatch", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command reports validation error when stored challenge is missing")
    void verifyStoredCredentialRequiresChallenge() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-verify-stored-missing-challenge");
        Path database = tempDir.resolve("store.db");
        seedCredentialWithMetadata(
                database, "stored-missing-challenge", DEFAULT_SUITE, null, Map.of("source", "cli-test"));

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database",
                database.toAbsolutePath().toString(),
                "verify",
                "--credential-id",
                "stored-missing-challenge",
                "--otp",
                "123456",
                "--counter",
                "0");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("challenge_required", telemetry.get("reasonCode"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("verify command requires suite for inline mode")
    void verifyInlineRequiresSuite() {
        CommandHarness harness = CommandHarness.create();

        int exitCode =
                harness.execute("verify", "--secret", DEFAULT_SECRET_HEX, "--otp", "123456", "--challenge", "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("suite_missing", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command surfaces validation error for invalid inline suite")
    void verifyInlineRejectsInvalidSuite() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "verify",
                "--suite",
                "INVALID-SUITE",
                "--secret",
                DEFAULT_SECRET_HEX,
                "--otp",
                "123456",
                "--challenge",
                "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("validation_error", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command rejects timestamp when suite does not permit it")
    void verifyInlineRejectsTimestampWhenNotPermitted() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "verify",
                "--suite",
                DEFAULT_SUITE,
                "--secret",
                DEFAULT_SECRET_HEX,
                "--otp",
                "123456",
                "--challenge",
                "12345678",
                "--timestamp",
                "00FF");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("validation_error", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command requires timestamp when suite expects it")
    void verifyInlineRequiresTimestampWhenExpected() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "verify",
                "--suite",
                "OCRA-1:HOTP-SHA1-6:QA08-T1",
                "--secret",
                DEFAULT_SECRET_HEX,
                "--otp",
                "123456",
                "--challenge",
                "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("validation_error", telemetry.get("reasonCode"));
    }

    @Test
    @DisplayName("verify command surfaces unexpected errors when store cannot open")
    void verifyStoredUnexpectedError() throws Exception {
        CommandHarness harness = CommandHarness.create();
        Path conflictingFile = Files.createTempFile("ocra-cli-verify", ".tmp");
        Path database = conflictingFile.resolve("nested/store.db");
        setDatabase(harness.cli(), database);

        int exitCode =
                harness.execute("verify", "--credential-id", "alpha", "--otp", "123456", "--challenge", "12345678");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.verify");
        assertEquals("error", telemetry.get("status"));
        assertEquals("unexpected_error", telemetry.get("reasonCode"));

        Files.deleteIfExists(conflictingFile);
    }

    @Test
    @DisplayName("verify helper mappings cover all reason codes")
    void verifyHandleInvalidReasonCodes() throws Exception {
        CommandHarness harness = CommandHarness.create();
        CommandLine verifyLine = harness.commandLine().getSubcommands().get("verify");
        assertTrue(verifyLine != null, "verify subcommand not registered");
        OcraCli.VerifyCommand command = verifyLine.getCommand();

        int validationExit =
                command.handleInvalid("cli.ocra.verify", VerificationReason.VALIDATION_FAILURE, new LinkedHashMap<>());
        assertEquals(CommandLine.ExitCode.USAGE, validationExit, harness.stderr());

        int credentialExit = command.handleInvalid(
                "cli.ocra.verify", VerificationReason.CREDENTIAL_NOT_FOUND, new LinkedHashMap<>());
        assertEquals(CommandLine.ExitCode.USAGE, credentialExit, harness.stderr());

        int unexpectedExit =
                command.handleInvalid("cli.ocra.verify", VerificationReason.UNEXPECTED_ERROR, new LinkedHashMap<>());
        assertEquals(CommandLine.ExitCode.SOFTWARE, unexpectedExit, harness.stderr());

        int unexpectedMatchExit =
                command.handleInvalid("cli.ocra.verify", VerificationReason.MATCH, new LinkedHashMap<>());
        assertEquals(CommandLine.ExitCode.SOFTWARE, unexpectedMatchExit, harness.stderr());
    }

    @Test
    @DisplayName("verify reasonCodeFor covers every verification reason")
    void verifyReasonCodeMapping() {
        assertEquals("match", OcraCli.VerifyCommand.reasonCodeFor(VerificationReason.MATCH));
        assertEquals("strict_mismatch", OcraCli.VerifyCommand.reasonCodeFor(VerificationReason.STRICT_MISMATCH));
        assertEquals("validation_error", OcraCli.VerifyCommand.reasonCodeFor(VerificationReason.VALIDATION_FAILURE));
        assertEquals(
                "credential_not_found", OcraCli.VerifyCommand.reasonCodeFor(VerificationReason.CREDENTIAL_NOT_FOUND));
        assertEquals("unexpected_error", OcraCli.VerifyCommand.reasonCodeFor(VerificationReason.UNEXPECTED_ERROR));
    }

    @Test
    @DisplayName("evaluate command rejects providing both credential and inline secret")
    void evaluateRejectsCredentialConflict() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "evaluate", "--credential-id", "account-1", "--secret", "31323334", "--challenge", "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.evaluate");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("credential_conflict", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("evaluate command requires suite when running in inline mode")
    void evaluateRequiresSuiteForInlineMode() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute("evaluate", "--secret", DEFAULT_SECRET_HEX, "--challenge", "12345678");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.evaluate");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("suite_missing", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("evaluate command surfaces validation errors from challenge mismatch")
    void evaluateSurfacesChallengeValidationErrors() {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "evaluate", "--suite", DEFAULT_SUITE, "--secret", DEFAULT_SECRET_HEX, "--challenge", "1234");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stdout() + harness.stderr(), "cli.ocra.evaluate");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("challenge_length", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("evaluate command generates OTP in inline mode")
    void evaluateGeneratesOtpInline() {
        CommandHarness harness = CommandHarness.create();

        String challenge = "12345678";
        int exitCode = harness.execute(
                "evaluate",
                "--suite",
                DEFAULT_SUITE,
                "--secret",
                DEFAULT_SECRET_HEX,
                "--challenge",
                challenge,
                "--window-backward",
                "1",
                "--window-forward",
                "1");

        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String stdout = harness.stdout();
        String otp = extractField(stdout, "otp");

        OcraCredentialDescriptor descriptor = new OcraCredentialFactory()
                .createDescriptor(new OcraCredentialRequest(
                        "cli-test", DEFAULT_SUITE, DEFAULT_SECRET_HEX, SecretEncoding.HEX, null, null, null, Map.of()));
        String expectedOtp = OcraResponseCalculator.generate(
                descriptor, new OcraExecutionContext(null, challenge, null, null, null, null, null));

        assertEquals(expectedOtp, otp);
        assertTrue(stdout.contains("mode=inline"));
        assertTrue(stdout.contains("Preview window:"), () -> "stdout:\n" + stdout);
        assertTrue(stdout.contains("[0]"), () -> "stdout:\n" + stdout);
        Map<String, String> telemetry = telemetryLine(stdout, "cli.ocra.evaluate");
        assertEquals("success", telemetry.get("status"));
        assertEquals("success", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("evaluate command supports stored credential mode")
    void evaluateStoredCredential() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-evaluate");
        Path database = tempDir.resolve("store.db");
        seedCredential(database, "alpha", DEFAULT_SUITE, null);

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database",
                database.toAbsolutePath().toString(),
                "evaluate",
                "--credential-id",
                "alpha",
                "--challenge",
                "12345678",
                "--window-backward",
                "1",
                "--window-forward",
                "1");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("credentialId=alpha"));
        assertTrue(stdout.contains("Preview window:"), () -> "stdout:\n" + stdout);
        assertTrue(stdout.contains("[0]"), () -> "stdout:\n" + stdout);
        Map<String, String> telemetry = telemetryLine(stdout, "cli.ocra.evaluate");
        assertEquals("success", telemetry.get("status"));
        assertEquals("success", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("list command prints stored credential summaries")
    void listCommandPrintsSummaries() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-list");
        Path database = tempDir.resolve("store.db");
        seedCredential(database, "alpha", "OCRA-1:HOTP-SHA1-6:QA08", null);
        seedCredential(database, "beta", "OCRA-1:HOTP-SHA1-6:C-QN08", 1L);

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute("--database", database.toAbsolutePath().toString(), "list");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("credentialId=alpha"));
        assertTrue(stdout.contains("credentialId=beta"));
        assertTrue(stdout.contains("suite=OCRA-1:HOTP-SHA1-6:QA08"));
        assertTrue(stdout.contains("suite=OCRA-1:HOTP-SHA1-6:C-QN08"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("list command verbose option prints metadata")
    void listCommandVerboseShowsMetadata() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-list-verbose");
        Path database = tempDir.resolve("store.db");
        seedCredential(database, "delta", DEFAULT_SUITE, null);

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute("--database", database.toAbsolutePath().toString(), "list", "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("metadata.source=cli-test"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("list command skips non-OCRA credentials in store")
    void listCommandSkipsNonOcraCredentials() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-list-filter");
        Path database = tempDir.resolve("store.db");
        seedCredential(database, "ocra-only", DEFAULT_SUITE, null);

        try (MapDbCredentialStore store = ocraStoreBuilder(database).open()) {
            store.save(Credential.create("generic", CredentialType.GENERIC, SecretMaterial.fromHex("00"), Map.of()));
        }

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute("--database", database.toAbsolutePath().toString(), "list", "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("count=1"));
        assertTrue(stdout.contains("credentialId=ocra-only"));
        assertTrue(!stdout.contains("credentialId=generic"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("list verbose output omits metadata section when descriptor metadata is empty")
    void listCommandVerboseOmitsEmptyMetadata() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-list-empty-metadata");
        Path database = tempDir.resolve("store.db");
        seedCredentialWithMetadata(database, "epsilon", DEFAULT_SUITE, null, Map.of());

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute("--database", database.toAbsolutePath().toString(), "list", "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("credentialId=epsilon"));
        assertTrue(!stdout.contains("metadata."));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("delete command removes stored credential and reports success")
    void deleteCommandRemovesCredential() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-delete");
        Path database = tempDir.resolve("store.db");
        seedCredential(database, "gamma", "OCRA-1:HOTP-SHA1-6:QA08", null);

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database", database.toAbsolutePath().toString(), "delete", "--credential-id", "gamma");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        Map<String, String> telemetry = telemetryLine(harness.stdout(), "cli.ocra.delete");
        assertEquals("success", telemetry.get("status"));
        assertEquals("deleted", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));

        // Confirm list no longer returns credential
        CommandHarness listHarness = CommandHarness.create();
        int listExit =
                listHarness.execute("--database", database.toAbsolutePath().toString(), "list");
        assertEquals(CommandLine.ExitCode.OK, listExit, listHarness.stderr());
        assertTrue(!listHarness.stdout().contains("credentialId=gamma"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("delete command reports credential_not_found when record missing")
    void deleteCommandReportsCredentialNotFound() throws Exception {
        Path tempDir = Files.createTempDirectory("ocra-cli-delete-missing");
        Path database = tempDir.resolve("store.db");

        CommandHarness harness = CommandHarness.create();
        int exitCode = harness.execute(
                "--database", database.toAbsolutePath().toString(), "delete", "--credential-id", "missing");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(harness.stderr(), "cli.ocra.delete");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("credential_not_found", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));

        deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("delete command treats invalid database path as validation error")
    void deleteCommandTreatsInvalidDatabaseAsValidationError() throws Exception {
        OcraCli cli = new OcraCli();
        CommandLine commandLine = new CommandLine(cli);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        setDatabase(cli, illegalDatabasePath());

        int exitCode = commandLine.execute("delete", "--credential-id", "invalid");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        Map<String, String> telemetry = telemetryLine(stderr.toString(StandardCharsets.UTF_8), "cli.ocra.delete");
        assertEquals("invalid", telemetry.get("status"));
        assertEquals("validation_error", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
    }

    @Test
    @DisplayName("delete command surfaces unexpected error when store initialization fails")
    void deleteCommandHandlesUnexpectedError() throws Exception {
        Path tempFile = Files.createTempFile("ocra-cli-delete", ".tmp");
        Path database = tempFile.resolve("store.db");

        OcraCli cli = new OcraCli();
        CommandLine commandLine = new CommandLine(cli);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        setDatabase(cli, database);

        int exitCode = commandLine.execute("delete", "--credential-id", "gamma");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        Map<String, String> telemetry = telemetryLine(stderr.toString(StandardCharsets.UTF_8), "cli.ocra.delete");
        assertEquals("error", telemetry.get("status"));
        assertEquals("unexpected_error", telemetry.get("reasonCode"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("emit helper omits blank fields and reason code when absent")
    void emitHelperOmitsBlankFields() {
        OcraCli cli = new OcraCli();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(buffer, true, StandardCharsets.UTF_8);

        cli.emit(writer, "cli.ocra.test", "success", null, true, Map.of("field", "value", "blank", "   "));

        Map<String, String> telemetry = telemetryLine(buffer.toString(StandardCharsets.UTF_8), "cli.ocra.test");
        assertEquals("success", telemetry.get("status"));
        assertEquals("unspecified", telemetry.get("reasonCode"));
        assertEquals("true", telemetry.get("sanitized"));
        assertEquals("value", telemetry.get("field"));
        assertFalse(telemetry.containsKey("blank"));
    }

    private static String extractField(String output, String field) {
        for (String token : output.split("\\s+")) {
            if (token.startsWith(field + "=")) {
                return token.substring(field.length() + 1);
            }
        }
        return null;
    }

    private static final class CommandHarness {
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final CommandLine commandLine;

        private CommandHarness() {
            commandLine = new CommandLine(new OcraCli());
            commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        }

        static CommandHarness create() {
            return new CommandHarness();
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

        OcraCli cli() {
            return commandLine.getCommand();
        }

        CommandLine commandLine() {
            return commandLine;
        }
    }

    private static void seedCredential(Path database, String credentialId, String suite, Long counter)
            throws Exception {
        seedCredentialWithMetadata(database, credentialId, suite, counter, Map.of("source", "cli-test"));
    }

    private static void seedCredentialWithMetadata(
            Path database, String credentialId, String suite, Long counter, Map<String, String> metadata)
            throws Exception {
        OcraCredentialFactory factory = new OcraCredentialFactory();

        OcraCredentialDescriptor descriptor = factory.createDescriptor(new OcraCredentialRequest(
                credentialId, suite, DEFAULT_SECRET_HEX, SecretEncoding.HEX, counter, null, null, metadata));

        persistDescriptor(database, descriptor);
    }

    private static void setDatabase(OcraCli cli, Path database) {
        cli.overrideDatabase(database);
    }

    private static Path illegalDatabasePath() {
        return TestPaths.failingAbsolutePath(
                Path.of("invalid-path"), new IllegalArgumentException("database path invalid"));
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort cleanup for temporary directories
                }
            });
        }
    }

    private static void persistDescriptor(Path database, OcraCredentialDescriptor descriptor) throws Exception {
        OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
        VersionedCredentialRecord record = adapter.serialize(descriptor);
        Credential credential = VersionedCredentialRecordMapper.toCredential(record);

        Path parent = database.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (MapDbCredentialStore store = ocraStoreBuilder(database).open()) {
            store.save(credential);
        }
    }

    private static MapDbCredentialStore.Builder ocraStoreBuilder(Path database) {
        return OcraStoreMigrations.apply(MapDbCredentialStore.file(database));
    }
}
