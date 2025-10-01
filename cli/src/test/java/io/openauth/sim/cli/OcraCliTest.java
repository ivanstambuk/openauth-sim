package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationReason;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

  private static final String DEFAULT_SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QN08";
  private static final String VERIFY_SUITE_COUNTER = "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1";
  private static final String VERIFY_SECRET_HEX_COUNTER =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String VERIFY_PIN_HASH_SHA1 = "7110eda4d09e062aa5e4a390b0a572ac0d2c0220";
  private static final long VERIFY_COUNTER = 0L;
  private static final String VERIFY_CHALLENGE_NUMERIC = "12345678";

  @Test
  @DisplayName("verify command matches stored credential when context is complete")
  void verifyStoredCredentialMatch() throws Exception {
    Path tempDir = Files.createTempDirectory("ocra-cli-verify-stored");
    Path database = tempDir.resolve("store.db");

    String credentialId = "verify-stored";
    OcraCredentialDescriptor descriptor =
        new OcraCredentialFactory()
            .createDescriptor(
                new OcraCredentialRequest(
                    credentialId,
                    VERIFY_SUITE_COUNTER,
                    VERIFY_SECRET_HEX_COUNTER,
                    SecretEncoding.HEX,
                    VERIFY_COUNTER,
                    VERIFY_PIN_HASH_SHA1,
                    null,
                    Map.of("source", "cli-verify")));
    persistDescriptor(database, descriptor);

    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(
                VERIFY_COUNTER,
                VERIFY_CHALLENGE_NUMERIC,
                null,
                null,
                null,
                VERIFY_PIN_HASH_SHA1,
                null));

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
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
    assertTrue(output.contains("event=cli.ocra.verify"));
    assertTrue(output.contains("status=match"));
    assertTrue(output.contains("reasonCode=match"));
    assertTrue(output.contains("credentialSource=stored"));

    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("verify command matches inline credential requests")
  void verifyInlineCredentialMatch() {
    OcraCredentialDescriptor descriptor =
        new OcraCredentialFactory()
            .createDescriptor(
                new OcraCredentialRequest(
                    "inline-verify",
                    DEFAULT_SUITE,
                    DEFAULT_SECRET_HEX,
                    SecretEncoding.HEX,
                    null,
                    null,
                    null,
                    Map.of("source", "cli-inline")));

    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(null, VERIFY_CHALLENGE_NUMERIC, null, null, null, null, null));

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
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
    assertTrue(output.contains("event=cli.ocra.verify"));
    assertTrue(output.contains("status=match"));
    assertTrue(output.contains("reasonCode=match"));
    assertTrue(output.contains("credentialSource=inline"));
  }

  @Test
  @DisplayName("verify command returns strict mismatch when counter differs")
  void verifyStoredCredentialStrictMismatch() throws Exception {
    Path tempDir = Files.createTempDirectory("ocra-cli-verify-mismatch");
    Path database = tempDir.resolve("store.db");

    String credentialId = "verify-mismatch";
    OcraCredentialDescriptor descriptor =
        new OcraCredentialFactory()
            .createDescriptor(
                new OcraCredentialRequest(
                    credentialId,
                    VERIFY_SUITE_COUNTER,
                    VERIFY_SECRET_HEX_COUNTER,
                    SecretEncoding.HEX,
                    VERIFY_COUNTER,
                    VERIFY_PIN_HASH_SHA1,
                    null,
                    Map.of("source", "cli-verify")));
    persistDescriptor(database, descriptor);

    String baselineOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(
                VERIFY_COUNTER,
                VERIFY_CHALLENGE_NUMERIC,
                null,
                null,
                null,
                VERIFY_PIN_HASH_SHA1,
                null));

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
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
    String output = harness.stdout() + harness.stderr();
    assertTrue(output.contains("event=cli.ocra.verify"));
    assertTrue(output.contains("status=mismatch"));
    assertTrue(output.contains("reasonCode=strict_mismatch"));

    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("verify command requires challenge context when suite expects it")
  void verifyRequiresChallengeContext() {
    OcraCredentialDescriptor descriptor =
        new OcraCredentialFactory()
            .createDescriptor(
                new OcraCredentialRequest(
                    "inline-verify-missing-context",
                    DEFAULT_SUITE,
                    DEFAULT_SECRET_HEX,
                    SecretEncoding.HEX,
                    null,
                    null,
                    null,
                    Map.of("source", "cli-inline")));

    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(null, VERIFY_CHALLENGE_NUMERIC, null, null, null, null, null));

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
            "verify",
            "--suite",
            DEFAULT_SUITE,
            "--secret",
            DEFAULT_SECRET_HEX,
            "--otp",
            expectedOtp);

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String output = harness.stdout() + harness.stderr();
    assertTrue(output.contains("event=cli.ocra.verify"));
    assertTrue(output.contains("reasonCode=validation_error"));
    assertTrue(output.toLowerCase(Locale.ROOT).contains("challenge"));
  }

  @Test
  @DisplayName("verify command requires OTP value")
  void verifyRequiresOtp() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute("verify", "--suite", DEFAULT_SUITE, "--secret", DEFAULT_SECRET_HEX);

    assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.verify"));
    assertTrue(stderr.contains("reasonCode=otp_missing"));
  }

  @Test
  @DisplayName("verify command rejects providing both credential id and inline secret")
  void verifyRejectsCredentialConflict() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
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
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=credential_conflict"));
  }

  @Test
  @DisplayName("verify command reports credential_not_found when stored credential missing")
  void verifyStoredCredentialNotFound() throws Exception {
    Path tempDir = Files.createTempDirectory("ocra-cli-verify-missing");
    Path database = tempDir.resolve("store.db");

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
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
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.verify"));
    assertTrue(stderr.contains("reasonCode=credential_not_found"));

    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("verify command requires credential id or inline secret")
  void verifyRequiresCredentialOrSecret() {
    CommandHarness harness = CommandHarness.create();

    int exitCode = harness.execute("verify", "--otp", "123456");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=credential_missing"));
  }

  @Test
  @DisplayName("verify command reports mismatch for inline credential OTP")
  void verifyInlineStrictMismatch() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
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
    String output = harness.stdout() + harness.stderr();
    assertTrue(output.contains("credentialSource=inline"));
    assertTrue(output.contains("status=mismatch"));
    assertTrue(output.contains("reasonCode=strict_mismatch"));
  }

  @Test
  @DisplayName("verify command reports validation error when stored challenge is missing")
  void verifyStoredCredentialRequiresChallenge() throws Exception {
    Path tempDir = Files.createTempDirectory("ocra-cli-verify-stored-missing-challenge");
    Path database = tempDir.resolve("store.db");
    seedCredentialWithMetadata(
        database, "stored-missing-challenge", DEFAULT_SUITE, null, Map.of("source", "cli-test"));

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
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
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=validation_error"));

    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("verify command requires suite for inline mode")
  void verifyInlineRequiresSuite() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
            "verify", "--secret", DEFAULT_SECRET_HEX, "--otp", "123456", "--challenge", "12345678");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode, harness.stdout() + harness.stderr());
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=suite_missing"));
  }

  @Test
  @DisplayName("verify command surfaces validation error for invalid inline suite")
  void verifyInlineRejectsInvalidSuite() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
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
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=validation_error"));
  }

  @Test
  @DisplayName("verify command rejects timestamp when suite does not permit it")
  void verifyInlineRejectsTimestampWhenNotPermitted() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
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
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=validation_error"));
  }

  @Test
  @DisplayName("verify command requires timestamp when suite expects it")
  void verifyInlineRequiresTimestampWhenExpected() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
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
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=validation_error"));
  }

  @Test
  @DisplayName("verify command surfaces unexpected errors when store cannot open")
  void verifyStoredUnexpectedError() throws Exception {
    CommandHarness harness = CommandHarness.create();
    Path conflictingFile = Files.createTempFile("ocra-cli-verify", ".tmp");
    Path database = conflictingFile.resolve("nested/store.db");
    setDatabase(harness.cli(), database);

    int exitCode =
        harness.execute(
            "verify", "--credential-id", "alpha", "--otp", "123456", "--challenge", "12345678");

    assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=unexpected_error"));

    Files.deleteIfExists(conflictingFile);
  }

  @Test
  @DisplayName("verify helper mappings cover all reason codes")
  void verifyHandleInvalidReasonCodes() throws Exception {
    CommandHarness harness = CommandHarness.create();
    CommandLine verifyLine = harness.commandLine().getSubcommands().get("verify");
    assertTrue(verifyLine != null, "verify subcommand not registered");
    OcraCli.VerifyCommand command = verifyLine.getCommand();

    Method handleInvalid =
        OcraCli.VerifyCommand.class.getDeclaredMethod(
            "handleInvalid", String.class, OcraVerificationReason.class, Map.class);
    handleInvalid.setAccessible(true);

    int validationExit =
        (int)
            handleInvalid.invoke(
                command,
                "cli.ocra.verify",
                OcraVerificationReason.VALIDATION_FAILURE,
                new LinkedHashMap<>());
    assertEquals(CommandLine.ExitCode.USAGE, validationExit, harness.stderr());

    int credentialExit =
        (int)
            handleInvalid.invoke(
                command,
                "cli.ocra.verify",
                OcraVerificationReason.CREDENTIAL_NOT_FOUND,
                new LinkedHashMap<>());
    assertEquals(CommandLine.ExitCode.USAGE, credentialExit, harness.stderr());

    int unexpectedExit =
        (int)
            handleInvalid.invoke(
                command,
                "cli.ocra.verify",
                OcraVerificationReason.UNEXPECTED_ERROR,
                new LinkedHashMap<>());
    assertEquals(CommandLine.ExitCode.SOFTWARE, unexpectedExit, harness.stderr());

    int unexpectedMatchExit =
        (int)
            handleInvalid.invoke(
                command, "cli.ocra.verify", OcraVerificationReason.MATCH, new LinkedHashMap<>());
    assertEquals(CommandLine.ExitCode.SOFTWARE, unexpectedMatchExit, harness.stderr());
  }

  @Test
  @DisplayName("verify reasonCodeFor covers every verification reason")
  void verifyReasonCodeMapping() throws Exception {
    Method reasonCodeFor =
        OcraCli.VerifyCommand.class.getDeclaredMethod(
            "reasonCodeFor", OcraVerificationReason.class);
    reasonCodeFor.setAccessible(true);

    assertEquals("match", reasonCodeFor.invoke(null, OcraVerificationReason.MATCH));
    assertEquals(
        "strict_mismatch", reasonCodeFor.invoke(null, OcraVerificationReason.STRICT_MISMATCH));
    assertEquals(
        "validation_error", reasonCodeFor.invoke(null, OcraVerificationReason.VALIDATION_FAILURE));
    assertEquals(
        "credential_not_found",
        reasonCodeFor.invoke(null, OcraVerificationReason.CREDENTIAL_NOT_FOUND));
    assertEquals(
        "unexpected_error", reasonCodeFor.invoke(null, OcraVerificationReason.UNEXPECTED_ERROR));
  }

  @Test
  @DisplayName("verify normalize trims whitespace to null")
  void verifyNormalizeWhitespace() throws Exception {
    Method normalize = OcraCli.VerifyCommand.class.getDeclaredMethod("normalize", String.class);
    normalize.setAccessible(true);

    assertEquals(null, normalize.invoke(null, "   "));
    assertEquals("abc", normalize.invoke(null, "  abc  "));
  }

  @Test
  @DisplayName("evaluate command rejects providing both credential and inline secret")
  void evaluateRejectsCredentialConflict() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
            "evaluate",
            "--credential-id",
            "account-1",
            "--secret",
            "31323334",
            "--challenge",
            "12345678");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=credential_conflict"));
    assertTrue(stderr.contains("sanitized=true"));
  }

  @Test
  @DisplayName("evaluate command requires suite when running in inline mode")
  void evaluateRequiresSuiteForInlineMode() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute("evaluate", "--secret", DEFAULT_SECRET_HEX, "--challenge", "12345678");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=suite_missing"));
    assertTrue(stderr.contains("suite is required for inline mode"));
  }

  @Test
  @DisplayName("evaluate command surfaces validation errors from challenge mismatch")
  void evaluateSurfacesChallengeValidationErrors() {
    CommandHarness harness = CommandHarness.create();

    int exitCode =
        harness.execute(
            "evaluate",
            "--suite",
            DEFAULT_SUITE,
            "--secret",
            DEFAULT_SECRET_HEX,
            "--challenge",
            "1234");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("reasonCode=validation_error"));
    assertTrue(stderr.contains("challengeQuestion must contain"));
  }

  @Test
  @DisplayName("evaluate command generates OTP in inline mode")
  void evaluateGeneratesOtpInline() {
    CommandHarness harness = CommandHarness.create();

    String challenge = "12345678";
    int exitCode =
        harness.execute(
            "evaluate",
            "--suite",
            DEFAULT_SUITE,
            "--secret",
            DEFAULT_SECRET_HEX,
            "--challenge",
            challenge);

    assertEquals(CommandLine.ExitCode.OK, exitCode);

    String stdout = harness.stdout();
    String otp = extractField(stdout, "otp");

    OcraCredentialDescriptor descriptor =
        new OcraCredentialFactory()
            .createDescriptor(
                new OcraCredentialRequest(
                    "cli-test",
                    DEFAULT_SUITE,
                    DEFAULT_SECRET_HEX,
                    SecretEncoding.HEX,
                    null,
                    null,
                    null,
                    Map.of()));
    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor, new OcraExecutionContext(null, challenge, null, null, null, null, null));

    assertEquals(expectedOtp, otp);
    assertTrue(stdout.contains("mode=inline"));
    assertTrue(stdout.contains("reasonCode=success"));
  }

  @Test
  @DisplayName("evaluate command supports stored credential mode")
  void evaluateStoredCredential() throws Exception {
    Path tempDir = Files.createTempDirectory("ocra-cli-evaluate");
    Path database = tempDir.resolve("store.db");
    seedCredential(database, "alpha", DEFAULT_SUITE, null);

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute(
            "--database",
            database.toAbsolutePath().toString(),
            "evaluate",
            "--credential-id",
            "alpha",
            "--challenge",
            "12345678");

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(stdout.contains("credentialId=alpha"));
    assertTrue(stdout.contains("reasonCode=success"));

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
    int exitCode =
        harness.execute("--database", database.toAbsolutePath().toString(), "list", "--verbose");

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

    try (MapDbCredentialStore store = MapDbCredentialStore.file(database).open()) {
      store.save(
          Credential.create(
              "generic", CredentialType.GENERIC, SecretMaterial.fromHex("00"), Map.of()));
    }

    CommandHarness harness = CommandHarness.create();
    int exitCode =
        harness.execute("--database", database.toAbsolutePath().toString(), "list", "--verbose");

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
    int exitCode =
        harness.execute("--database", database.toAbsolutePath().toString(), "list", "--verbose");

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
    int exitCode =
        harness.execute(
            "--database",
            database.toAbsolutePath().toString(),
            "delete",
            "--credential-id",
            "gamma");

    assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
    assertTrue(harness.stdout().contains("reasonCode=deleted"));

    // Confirm list no longer returns credential
    CommandHarness listHarness = CommandHarness.create();
    int listExit = listHarness.execute("--database", database.toAbsolutePath().toString(), "list");
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
    int exitCode =
        harness.execute(
            "--database",
            database.toAbsolutePath().toString(),
            "delete",
            "--credential-id",
            "missing");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.delete"));
    assertTrue(stderr.contains("reasonCode=credential_not_found"));
    assertTrue(stderr.contains("status=invalid"));

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
    String err = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("event=cli.ocra.delete"));
    assertTrue(err.contains("reasonCode=validation_error"));
    assertTrue(err.contains("sanitized=true"));
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
    String err = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("event=cli.ocra.delete"));
    assertTrue(err.contains("status=error"));
    assertTrue(err.contains("reasonCode=unexpected_error"));

    Files.deleteIfExists(tempFile);
  }

  @Test
  @DisplayName("emit helper omits blank fields and reason code when absent")
  void emitHelperOmitsBlankFields() throws Exception {
    OcraCli cli = new OcraCli();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintWriter writer = new PrintWriter(buffer, true, StandardCharsets.UTF_8);

    java.lang.reflect.Method emit =
        OcraCli.class.getDeclaredMethod(
            "emit",
            PrintWriter.class,
            String.class,
            String.class,
            String.class,
            boolean.class,
            Map.class);
    emit.setAccessible(true);

    emit.invoke(
        cli,
        writer,
        "cli.ocra.test",
        "success",
        null,
        true,
        Map.of("field", "value", "blank", "   "));

    String output = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("event=cli.ocra.test"));
    assertTrue(output.contains("status=success"));
    assertTrue(output.contains("sanitized=true"));
    assertTrue(output.contains("field=value"));
    assertFalse(output.contains("blank="));
    assertFalse(output.contains("reasonCode="));
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
    seedCredentialWithMetadata(
        database, credentialId, suite, counter, Map.of("source", "cli-test"));
  }

  private static void seedCredentialWithMetadata(
      Path database, String credentialId, String suite, Long counter, Map<String, String> metadata)
      throws Exception {
    OcraCredentialFactory factory = new OcraCredentialFactory();

    OcraCredentialDescriptor descriptor =
        factory.createDescriptor(
            new OcraCredentialRequest(
                credentialId,
                suite,
                DEFAULT_SECRET_HEX,
                SecretEncoding.HEX,
                counter,
                null,
                null,
                metadata));

    persistDescriptor(database, descriptor);
  }

  private static void setDatabase(OcraCli cli, Path database) throws Exception {
    var field = OcraCli.class.getDeclaredField("database");
    field.setAccessible(true);
    field.set(cli, database);
  }

  private static Path illegalDatabasePath() {
    Path delegate = Path.of("invalid-path");
    InvocationHandler handler =
        (proxy, method, args) -> {
          if ("toAbsolutePath".equals(method.getName())) {
            throw new IllegalArgumentException("database path invalid");
          }
          try {
            return method.invoke(delegate, args);
          } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof RuntimeException runtime) {
              throw runtime;
            }
            throw ex;
          }
        };
    return (Path)
        Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[] {Path.class}, handler);
  }

  private static void deleteRecursively(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths
          .sorted((a, b) -> b.compareTo(a))
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // best-effort cleanup for temporary directories
                }
              });
    }
  }

  private static void persistDescriptor(Path database, OcraCredentialDescriptor descriptor)
      throws Exception {
    OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
    VersionedCredentialRecord record = adapter.serialize(descriptor);
    Credential credential = VersionedCredentialRecordMapper.toCredential(record);

    Path parent = database.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (MapDbCredentialStore store = MapDbCredentialStore.file(database).open()) {
      store.save(credential);
    }
  }
}
