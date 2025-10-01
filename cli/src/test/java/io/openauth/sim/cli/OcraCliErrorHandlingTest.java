package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.MapDbCredentialStore;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class OcraCliErrorHandlingTest {

  private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QA08";
  private static final String DEFAULT_SECRET_HEX = "3132333435363738393031323334353637383930";

  private final CommandHarness harness = CommandHarness.create();

  @AfterEach
  void cleanup() {
    harness.cleanup();
  }

  @Test
  @DisplayName("failValidation emits sanitized telemetry")
  void failValidationSanitizesOutput() {
    harness.reset();
    OcraCli.DeleteCommand command = new OcraCli.DeleteCommand();
    command.parent = harness.parent();

    int exit = command.failValidation("cli.ocra.test", "invalid_request", "bad\ninput");

    assertEquals(CommandLine.ExitCode.USAGE, exit);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.test"));
    assertTrue(stderr.contains("status=invalid"));
    assertTrue(stderr.contains("reasonCode=invalid_request"));
    assertTrue(stderr.contains("sanitized=true"));
    assertTrue(stderr.contains("reason=bad input"));
  }

  @Test
  @DisplayName("failUnexpected emits unsanitized telemetry with software exit code")
  void failUnexpectedEmitsUnsanitizedOutput() {
    harness.reset();
    OcraCli.DeleteCommand command = new OcraCli.DeleteCommand();
    command.parent = harness.parent();

    int exit = command.failUnexpected("cli.ocra.test", "boom");

    assertEquals(CommandLine.ExitCode.SOFTWARE, exit);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.test"));
    assertTrue(stderr.contains("status=error"));
    assertTrue(stderr.contains("reasonCode=unexpected_error"));
    assertTrue(stderr.contains("sanitized=false"));
    assertTrue(stderr.contains("reason=boom"));
  }

  @Test
  @DisplayName("openStore creates parent directories when needed")
  void openStoreCreatesParentDirectories() throws Exception {
    harness.reset();
    OcraCli parent = harness.parent();

    Path tempDir = Files.createTempDirectory("ocra-cli-open-store");
    Path database = tempDir.resolve("nested").resolve("store.db");
    Path parentDirectory = Objects.requireNonNull(database.getParent(), "parentDirectory");

    Files.deleteIfExists(parentDirectory);
    parent.overrideDatabase(database);

    try {
      int exit = harness.execute("list");
      assertEquals(CommandLine.ExitCode.OK, exit, harness.stderr());
      assertTrue(Files.exists(parentDirectory));
    } finally {
      deleteRecursively(tempDir);
    }
  }

  @Test
  @DisplayName("import command surfaces validation errors")
  void importCommandValidationFailure() {
    harness.reset();
    int exit =
        harness.execute(
            "import", "--credential-id", "alpha", "--suite", DEFAULT_SUITE, "--secret", "not-hex");

    assertEquals(CommandLine.ExitCode.USAGE, exit);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.import"));
    assertTrue(stderr.contains("reasonCode=validation_error"));
  }

  @Test
  @DisplayName("import command reports unexpected errors from store creation")
  void importCommandUnexpectedError() throws Exception {
    Path directory = Files.createTempDirectory("ocra-cli-import-dir");
    harness.reset();
    int exit =
        harness.execute(
            "--database",
            directory.toAbsolutePath().toString(),
            "import",
            "--credential-id",
            "beta",
            "--suite",
            DEFAULT_SUITE,
            "--secret",
            DEFAULT_SECRET_HEX);

    assertEquals(CommandLine.ExitCode.SOFTWARE, exit);
    String stderr = harness.stderr();
    assertTrue(stderr.contains("event=cli.ocra.import"));
    assertTrue(stderr.contains("reasonCode=unexpected_error"));
    assertTrue(stderr.contains("sanitized=false"));

    Files.walk(directory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception ignored) {
                // best-effort cleanup
              }
            });
  }

  @Test
  @DisplayName("import command persists credentials on success")
  void importCommandSuccess() throws Exception {
    Path directory = Files.createTempDirectory("ocra-cli-import-success");
    Path database = directory.resolve("store.db");
    harness.reset();

    int exit =
        harness.execute(
            "--database",
            database.toAbsolutePath().toString(),
            "import",
            "--credential-id",
            "gamma",
            "--suite",
            DEFAULT_SUITE,
            "--secret",
            DEFAULT_SECRET_HEX);

    assertEquals(CommandLine.ExitCode.OK, exit, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(stdout.contains("event=cli.ocra.import"));
    assertTrue(stdout.contains("status=success"));
    assertTrue(stdout.contains("reasonCode=created"));

    Files.walk(directory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception ignored) {
                // best-effort cleanup
              }
            });
  }

  @Test
  @DisplayName("import command accepts PIN hash and drift overrides")
  void importCommandWithPinAndDrift() throws Exception {
    Path directory = Files.createTempDirectory("ocra-cli-import-pin");
    Path database = directory.resolve("store.db");
    harness.reset();

    int exit =
        harness.execute(
            "--database",
            database.toAbsolutePath().toString(),
            "import",
            "--credential-id",
            "delta",
            "--suite",
            "OCRA-1:HOTP-SHA1-6:QA08-PSHA1",
            "--secret",
            DEFAULT_SECRET_HEX,
            "--pin-hash",
            DEFAULT_SECRET_HEX,
            "--drift-seconds",
            "120");

    assertEquals(CommandLine.ExitCode.OK, exit, harness.stderr());
    String stdout = harness.stdout();
    assertTrue(stdout.contains("reasonCode=created"));

    Files.walk(directory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception ignored) {
                // best-effort cleanup
              }
            });
  }

  @Test
  @DisplayName("resolveDescriptor skips non-OCRA credentials")
  void resolveDescriptorFiltersNonOcraCredentials() throws Exception {
    harness.reset();
    OcraCli.ListCommand command = new OcraCli.ListCommand();
    command.parent = harness.parent();

    try (MapDbCredentialStore store = MapDbCredentialStore.inMemory().open()) {
      store.save(
          Credential.create(
              "basic", CredentialType.GENERIC, SecretMaterial.fromHex("00"), Map.of()));

      Optional<OcraCredentialDescriptor> descriptor = command.resolveDescriptor(store, "basic");
      assertTrue(descriptor.isEmpty());
    }
  }

  @Test
  @DisplayName("list command treats OCRA deserialisation issues as validation errors")
  void listCommandHandlesInvalidRecord() throws Exception {
    Path directory = Files.createTempDirectory("ocra-cli-list-invalid");
    Path database = directory.resolve("store.db");

    try {
      seedInvalidOcraRecord(database);

      harness.reset();
      int exit = harness.execute("--database", database.toAbsolutePath().toString(), "list");

      assertEquals(CommandLine.ExitCode.USAGE, exit);
      String stderr = harness.stderr();
      assertTrue(stderr.contains("event=cli.ocra.list"));
      assertTrue(stderr.contains("reasonCode=validation_error"));
      assertTrue(stderr.contains("sanitized=true"));
    } finally {
      deleteRecursively(directory);
    }
  }

  @Test
  @DisplayName("list command surfaces unexpected exceptions")
  void listCommandHandlesUnexpectedError() throws Exception {
    Path directory = Files.createTempDirectory("ocra-cli-list-unexpected");
    Path database = directory.resolve("store.db");

    try {
      harness.reset();
      int importExit =
          harness.execute(
              "--database",
              database.toAbsolutePath().toString(),
              "import",
              "--credential-id",
              "omega",
              "--suite",
              DEFAULT_SUITE,
              "--secret",
              DEFAULT_SECRET_HEX);

      assertEquals(CommandLine.ExitCode.OK, importExit, harness.stderr());

      OcraCli.ListCommand listCommand = listCommand();
      var previous = listCommand.swapPersistenceAdapter(null);

      try {
        harness.reset();
        int exit = harness.execute("--database", database.toAbsolutePath().toString(), "list");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exit);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.ocra.list"));
        assertTrue(stderr.contains("reasonCode=unexpected_error"));
        assertTrue(stderr.contains("sanitized=false"));
      } finally {
        listCommand.swapPersistenceAdapter(previous);
      }
    } finally {
      deleteRecursively(directory);
    }
  }

  @Test
  @DisplayName("delete command treats illegal database path as validation error")
  void deleteCommandHandlesIllegalDatabasePath() throws Exception {
    harness.reset();
    OcraCli parent = harness.parent();
    Path original = parent.configuredDatabase();
    Path illegalPath = illegalDatabasePath();

    try {
      parent.overrideDatabase(illegalPath);
      int exit = harness.execute("delete", "--credential-id", "alpha");

      assertEquals(CommandLine.ExitCode.USAGE, exit);
      String stderr = harness.stderr();
      assertTrue(stderr.contains("event=cli.ocra.delete"));
      assertTrue(stderr.contains("reasonCode=validation_error"));
      assertTrue(stderr.contains("sanitized=true"));
    } finally {
      parent.overrideDatabase(original);
    }
  }

  @Test
  @DisplayName("delete command surfaces unexpected errors when store creation fails")
  void deleteCommandHandlesUnexpectedStoreError() throws Exception {
    harness.reset();
    OcraCli parent = harness.parent();
    Path parentFile = Files.createTempFile("ocra-cli-delete", ".tmp");
    Path database = parentFile.resolve("store.db");
    Path original = parent.configuredDatabase();

    try {
      parent.overrideDatabase(database);
      int exit = harness.execute("delete", "--credential-id", "zeta");

      assertEquals(CommandLine.ExitCode.SOFTWARE, exit);
      String stderr = harness.stderr();
      assertTrue(stderr.contains("event=cli.ocra.delete"));
      assertTrue(stderr.contains("reasonCode=unexpected_error"));
      assertTrue(stderr.contains("sanitized=false"));
    } finally {
      parent.overrideDatabase(original);
      Files.deleteIfExists(parentFile);
    }
  }

  private static final class CommandHarness {
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private final CommandLine commandLine;
    private final OcraCli cli;

    private CommandHarness() {
      this.cli = new OcraCli();
      this.commandLine = new CommandLine(cli);
      commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
      commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
    }

    static CommandHarness create() {
      return new CommandHarness();
    }

    void reset() {
      stdout.reset();
      stderr.reset();
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

    OcraCli parent() {
      return cli;
    }

    void cleanup() {
      stdout.reset();
      stderr.reset();
    }

    CommandLine commandLine() {
      return commandLine;
    }
  }

  private OcraCli.ListCommand listCommand() {
    return (OcraCli.ListCommand) harness.commandLine().getSubcommands().get("list").getCommand();
  }

  private static void seedInvalidOcraRecord(Path database) throws Exception {
    try (MapDbCredentialStore store = MapDbCredentialStore.file(database).open()) {
      Map<String, String> attributes = new HashMap<>();
      attributes.put(OcraCredentialPersistenceAdapter.ATTR_SUITE, DEFAULT_SUITE);
      attributes.put(OcraCredentialPersistenceAdapter.ATTR_METADATA_PREFIX, "value");
      store.save(
          Credential.create(
              "invalid",
              CredentialType.OATH_OCRA,
              SecretMaterial.fromHex(DEFAULT_SECRET_HEX),
              attributes));
    }
  }

  private static void deleteRecursively(Path directory) throws Exception {
    if (!Files.exists(directory)) {
      return;
    }
    Files.walk(directory)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception ignored) {
                // best-effort cleanup
              }
            });
  }

  private static Path illegalDatabasePath() {
    return TestPaths.failingAbsolutePath(
        Path.of("invalid-path"), new IllegalArgumentException("database path invalid"));
  }
}
