package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class OcraCliTest {

  private static final String DEFAULT_SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QN08";

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
  @DisplayName("delete command reports missing credential")
  void deleteCommandMissingCredential() throws Exception {
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
    assertTrue(harness.stderr().contains("credential_not_found"));

    deleteRecursively(tempDir);
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
  }

  private static void seedCredential(Path database, String credentialId, String suite, Long counter)
      throws Exception {
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();

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
                Map.of("source", "cli-test")));

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
}
