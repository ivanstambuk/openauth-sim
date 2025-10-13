package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraOneWayVector;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class OcraCliCommandTest {

  private static final OcraOneWayVector CHALLENGE_ZERO_VECTOR =
      OcraJsonVectorFixtures.getOneWay("rfc6287_standard-challenge-question-numeric-q");
  private static final OcraOneWayVector CHALLENGE_ONE_VECTOR =
      OcraJsonVectorFixtures.getOneWay("rfc6287_standard-challenge-question-repeated-digits");

  private static final String STANDARD_KEY_20 =
      CHALLENGE_ZERO_VECTOR.secret().asHex().toUpperCase(Locale.ROOT);
  private static final String CHALLENGE_ZERO =
      CHALLENGE_ZERO_VECTOR
          .challengeQuestion()
          .orElseThrow(() -> new IllegalStateException("Numeric challenge missing value"));
  private static final String CHALLENGE_ONE =
      CHALLENGE_ONE_VECTOR
          .challengeQuestion()
          .orElseThrow(() -> new IllegalStateException("Repeated digits challenge missing value"));
  private static final String OCRA_SUITE_QN08 = CHALLENGE_ZERO_VECTOR.suite();

  @TempDir Path tempDir;

  @Test
  void importCommandPersistsCredentialAndEmitsSanitizedTelemetry() throws Exception {
    Path databasePath = databasePath("import");

    CliResult result =
        execute(
            databasePath,
            "import",
            "--credential-id",
            "cli-token",
            "--suite",
            OCRA_SUITE_QN08,
            "--secret",
            STANDARD_KEY_20);

    assertEquals(0, result.exitCode(), () -> "stderr was: " + result.stderr());
    assertTrue(result.stdout().contains("event=cli.ocra.import"));
    assertTrue(result.stdout().contains("status=success"));
    assertTrue(result.stdout().contains("credentialId=cli-token"));
    assertTrue(result.stdout().contains("suite=" + OCRA_SUITE_QN08));
    assertTrue(result.stdout().contains("sanitized=true"));
    assertFalse(
        result.stdout().contains(STANDARD_KEY_20.substring(0, 8)), "secret leaked to stdout");

    try (MapDbCredentialStore store = ocraStoreBuilder(databasePath).open()) {
      assertTrue(store.findByName("cli-token").isPresent());
    }
  }

  @Test
  void listCommandRedactsSecretsWhileReportingMetadata() throws Exception {
    Path databasePath = databasePath("list");
    persistOcraCredential(databasePath, "list-token", STANDARD_KEY_20, OCRA_SUITE_QN08, null);

    CliResult result = execute(databasePath, "list");

    assertEquals(0, result.exitCode(), () -> "stderr was: " + result.stderr());
    assertTrue(result.stdout().contains("event=cli.ocra.list"));
    assertTrue(result.stdout().contains("status=success"));
    assertTrue(result.stdout().contains("credentialId=list-token"));
    assertTrue(result.stdout().contains("suite=" + OCRA_SUITE_QN08));
    assertTrue(result.stdout().contains("sanitized=true"));
    assertFalse(
        result.stdout().contains(STANDARD_KEY_20.substring(0, 8)), "secret leaked to stdout");
  }

  @Test
  void deleteCommandRemovesCredentialAndLogsOutcome() throws Exception {
    Path databasePath = databasePath("delete");
    persistOcraCredential(databasePath, "delete-token", STANDARD_KEY_20, OCRA_SUITE_QN08, null);

    CliResult result = execute(databasePath, "delete", "--credential-id", "delete-token");

    assertEquals(0, result.exitCode(), () -> "stderr was: " + result.stderr());
    assertTrue(result.stdout().contains("event=cli.ocra.delete"));
    assertTrue(result.stdout().contains("status=success"));
    assertTrue(result.stdout().contains("credentialId=delete-token"));
    assertTrue(result.stdout().contains("reasonCode=deleted"));
    assertFalse(
        result.stdout().contains(STANDARD_KEY_20.substring(0, 8)), "secret leaked to stdout");

    try (MapDbCredentialStore store = ocraStoreBuilder(databasePath).open()) {
      assertTrue(store.findByName("delete-token").isEmpty());
    }
  }

  @Test
  void deleteCommandSurfacesNotFoundWhenCredentialMissing() {
    Path databasePath = databasePath("delete-missing");

    CliResult result = execute(databasePath, "delete", "--credential-id", "unknown-token");

    assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
    assertTrue(result.stderr().contains("event=cli.ocra.delete"));
    assertTrue(result.stderr().contains("reasonCode=credential_not_found"));
    assertTrue(result.stderr().contains("sanitized=true"));
  }

  @Test
  void evaluateWithCredentialReferenceGeneratesExpectedOtp() throws Exception {
    Path databasePath = databasePath("evaluate-store");
    OcraCredentialDescriptor descriptor =
        persistOcraCredential(
            databasePath, "evaluate-token", STANDARD_KEY_20, OCRA_SUITE_QN08, null);

    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(null, CHALLENGE_ZERO, null, null, null, null, null));

    CliResult result =
        execute(
            databasePath,
            "evaluate",
            "--credential-id",
            "evaluate-token",
            "--challenge",
            CHALLENGE_ZERO);

    assertEquals(0, result.exitCode(), () -> "stderr was: " + result.stderr());
    assertTrue(result.stdout().contains("event=cli.ocra.evaluate"));
    assertTrue(result.stdout().contains("status=success"));
    assertTrue(result.stdout().contains("credentialId=evaluate-token"));
    assertTrue(result.stdout().contains("otp=" + expectedOtp));
    assertTrue(result.stdout().contains("reasonCode=success"));
    assertFalse(
        result.stdout().contains(STANDARD_KEY_20.substring(0, 8)), "secret leaked to stdout");
  }

  @Test
  void evaluateWithInlineSecretSupportsAlternateChallenge() {
    Path databasePath = databasePath("evaluate-inline");

    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialDescriptor descriptor =
        factory.createDescriptor(
            new OcraCredentialRequest(
                "inline-token",
                OCRA_SUITE_QN08,
                STANDARD_KEY_20,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "inline-test")));

    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(null, CHALLENGE_ONE, null, null, null, null, null));

    CliResult result =
        execute(
            databasePath,
            "evaluate",
            "--suite",
            OCRA_SUITE_QN08,
            "--secret",
            STANDARD_KEY_20,
            "--challenge",
            CHALLENGE_ONE);

    assertEquals(0, result.exitCode(), () -> "stderr was: " + result.stderr());
    assertTrue(result.stdout().contains("event=cli.ocra.evaluate"));
    assertTrue(result.stdout().contains("status=success"));
    assertTrue(result.stdout().contains("otp=" + expectedOtp));
    assertTrue(result.stdout().contains("reasonCode=success"));
    assertTrue(result.stdout().contains("sanitized=true"));
    assertFalse(
        result.stdout().contains(STANDARD_KEY_20.substring(0, 8)), "secret leaked to stdout");
  }

  @Test
  void listCommandVerboseEmitsMetadataEntries() throws Exception {
    Path databasePath = databasePath("list-verbose");
    persistOcraCredential(
        databasePath, "list-verbose-token", STANDARD_KEY_20, OCRA_SUITE_QN08, null);

    CliResult result = execute(databasePath, "list", "--verbose");

    assertEquals(0, result.exitCode(), () -> "stderr was: " + result.stderr());
    assertTrue(result.stdout().contains("event=cli.ocra.list"));
    assertTrue(result.stdout().contains("count=1"));
    assertTrue(result.stdout().contains("metadata.source=cli-test"));
  }

  private CliResult execute(Path databasePath, String... args) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    CommandLine commandLine = new CommandLine(new OcraCli());
    commandLine.setOut(
        new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8), true));
    commandLine.setErr(
        new PrintWriter(new OutputStreamWriter(stderr, StandardCharsets.UTF_8), true));

    String[] finalArgs = new String[args.length + 2];
    finalArgs[0] = "--database";
    finalArgs[1] = databasePath.toString();
    System.arraycopy(args, 0, finalArgs, 2, args.length);

    int exitCode = commandLine.execute(finalArgs);
    return new CliResult(
        exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
  }

  private OcraCredentialDescriptor persistOcraCredential(
      Path databasePath,
      String credentialId,
      String sharedSecretHex,
      String ocraSuite,
      Long counter)
      throws Exception {
    Path parent = databasePath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialDescriptor descriptor =
        factory.createDescriptor(
            new OcraCredentialRequest(
                credentialId,
                ocraSuite,
                sharedSecretHex,
                SecretEncoding.HEX,
                counter,
                null,
                null,
                Map.of("source", "cli-test")));

    try (MapDbCredentialStore store = ocraStoreBuilder(databasePath).open()) {
      store.save(
          VersionedCredentialRecordMapper.toCredential(
              new OcraCredentialPersistenceAdapter().serialize(descriptor)));
    }

    return descriptor;
  }

  private MapDbCredentialStore.Builder ocraStoreBuilder(Path databasePath) {
    return OcraStoreMigrations.apply(MapDbCredentialStore.file(databasePath));
  }

  private Path databasePath(String prefix) {
    return tempDir.resolve(prefix + "-store.db");
  }

  private record CliResult(int exitCode, String stdout, String stderr) {
    // Marker record; no additional members required for assertions.
  }

  @Test
  void abstractCommandIsSealedAndPermitsKnownSubcommands() {
    Class<OcraCli.AbstractOcraCommand> type = OcraCli.AbstractOcraCommand.class;

    assertTrue(type.isSealed(), "Abstract command should be sealed");

    Set<String> permitted =
        Arrays.stream(type.getPermittedSubclasses())
            .map(Class::getName)
            .collect(Collectors.toSet());

    assertEquals(
        Set.of(
            "io.openauth.sim.cli.OcraCli$ImportCommand",
            "io.openauth.sim.cli.OcraCli$ListCommand",
            "io.openauth.sim.cli.OcraCli$DeleteCommand",
            "io.openauth.sim.cli.OcraCli$EvaluateCommand",
            "io.openauth.sim.cli.OcraCli$VerifyCommand"),
        permitted,
        () -> "permitted list did not match: " + permitted);
  }
}
