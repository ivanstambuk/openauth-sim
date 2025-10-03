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
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.store.testing.MapDbMaintenanceFixtures;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaintenanceCliTest {

  private static final String DEFAULT_SUITE = "OCRA-1:HOTP-SHA1-6:QN08";
  private static final String DEFAULT_SECRET_HEX = "31323334353637383930313233343536";
  private static final String DEFAULT_CHALLENGE = "12345678";

  @Test
  @DisplayName("run without arguments prints usage and exits with error")
  void runWithoutArgumentsShowsUsage() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("usage:"), harness.err());
  }

  @Test
  @DisplayName("run with null arguments prints usage and exits with error")
  void runWithNullArgumentsShowsUsage() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(null, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("usage:"), harness.err());
  }

  @Test
  @DisplayName("main exits process when run returns non-zero")
  void mainExitsProcessWhenRunFails() throws Exception {
    List<String> command = new ArrayList<>();
    command.add(javaCommand());
    String agent = jacocoAgentArgument();
    if (agent != null) {
      command.add(agent);
    }
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(MaintenanceCli.class.getName());
    command.add("compact");

    Process process = new ProcessBuilder().command(command).redirectErrorStream(true).start();

    try (InputStream stdout = process.getInputStream()) {
      int status = process.waitFor();
      String body = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(1, status, () -> body);
      assertTrue(body.contains("--database=<path> is required"), body);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  @Test
  @DisplayName("ocra command enforces required suite parameter")
  void ocraCommandRequiresSuite() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {
              "ocra", "--key=" + DEFAULT_SECRET_HEX, "--challenge=" + DEFAULT_CHALLENGE
            },
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("--suite=<ocra-suite> is required"), harness.err());
  }

  @Test
  @DisplayName("ocra command generates OTP for valid inline request")
  void ocraCommandGeneratesOtp() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {
              "ocra",
              "--suite=" + DEFAULT_SUITE,
              "--key=" + DEFAULT_SECRET_HEX,
              "--challenge=" + DEFAULT_CHALLENGE
            },
            harness.out,
            harness.err);

    assertEquals(0, exitCode, harness.err());

    String stdout = harness.out();
    String otpValue = extractField(stdout, "otp");

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
                    Map.of("source", "test")));
    String expectedOtp =
        OcraResponseCalculator.generate(
            descriptor,
            new OcraExecutionContext(null, DEFAULT_CHALLENGE, null, null, null, null, null));

    assertTrue(stdout.contains("suite=" + DEFAULT_SUITE), stdout);
    assertEquals(expectedOtp, otpValue);
    assertTrue(harness.err().isBlank());
  }

  @Test
  @DisplayName("parseOcraArguments captures optional parameters")
  void parseOcraArgumentsCapturesOptionalParameters() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    String[] args = {
      "ocra",
      "--suite=" + DEFAULT_SUITE,
      "--key=" + DEFAULT_SECRET_HEX,
      "--challenge=" + DEFAULT_CHALLENGE,
      "--session=ABCDEF12",
      "--client=CAFEBABE",
      "--server=DEADBEEF",
      "--pin=5E884898DA280471",
      "--timestamp=00000001",
      "--counter=2"
    };

    MaintenanceCli.OcraArguments parsed = cli.parseOcraArguments(args, harness.err);

    assertTrue(parsed.sessionInformation().isPresent());
    assertTrue(parsed.clientChallenge().isPresent());
    assertTrue(parsed.serverChallenge().isPresent());
    assertTrue(parsed.pinHashHex().isPresent());
    assertTrue(parsed.timestampHex().isPresent());
    assertTrue(parsed.counter().isPresent());
    assertTrue(harness.err().isBlank());
  }

  @Test
  @DisplayName("unknown command prints usage and fails")
  void unknownCommandFails() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"unknown"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("unknown command"), harness.err());
  }

  @Test
  @DisplayName("compact command executes against a temporary database")
  void compactCommandSucceeds() throws Exception {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();
    Path tempDir = Files.createTempDirectory("maintenance-cli-compact");
    Path database = tempDir.resolve("store.db");

    importCredential(database, "cred-compact");

    int exitCode =
        cli.run(
            new String[] {"compact", "--database=" + database.toAbsolutePath()},
            harness.out,
            harness.err);

    String stdout = harness.out();
    assertEquals(0, exitCode, harness.err());
    assertTrue(stdout.contains("operation=COMPACTION"), stdout);
    assertTrue(stdout.contains("status="), stdout);

    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("compact command requires an explicit database path")
  void compactCommandRequiresDatabase() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"compact"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("--database=<path> is required"), harness.err());
  }

  @Test
  @DisplayName("compact command rejects unknown options")
  void compactCommandRejectsUnknownOptions() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {"compact", "--database=/tmp/db", "--unknown=flag"},
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("unrecognised option"), harness.err());
  }

  @Test
  @DisplayName("compact command reports directory preparation failures")
  void compactCommandReportsDirectoryFailure() throws Exception {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();
    Path conflictingFile = Files.createTempFile("maintenance-cli-file", ".tmp");

    Path database = conflictingFile.resolve("nested/store.db");

    int exitCode =
        cli.run(
            new String[] {"compact", "--database=" + database.toAbsolutePath()},
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("unable to prepare database directory"), harness.err());

    Files.deleteIfExists(conflictingFile);
  }

  @Test
  @DisplayName("compact command handles relative database path without parent directory")
  void compactCommandHandlesRelativeDatabasePath() throws Exception {
    Path workingDir = Files.createTempDirectory("maintenance-cli-relative");
    Path database = workingDir.resolve("store.db");

    importCredential(database, "relative-compact");

    List<String> command = new ArrayList<>();
    command.add(javaCommand());
    String agent = jacocoAgentArgument();
    if (agent != null) {
      command.add(agent);
    }
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(MaintenanceCli.class.getName());
    command.add("compact");
    command.add("--database=store.db");

    Process process =
        new ProcessBuilder()
            .command(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start();

    try (InputStream stdout = process.getInputStream()) {
      int status = process.waitFor();
      String body = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, status, () -> body);
      assertTrue(body.contains("operation=COMPACTION"), body);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
      deleteRecursively(workingDir);
    }
  }

  @Test
  @DisplayName("ocra command surfaces validation failures")
  void ocraCommandReportsValidationFailures() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {
              "ocra",
              "--suite=OCRA-1:HOTP-SHA1-6:QN08",
              "--key=XYZ",
              "--challenge=" + DEFAULT_CHALLENGE
            },
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("error:"), harness.err());
    assertTrue(harness.err().contains("even number of characters"), harness.err());
  }

  @Test
  @DisplayName("ocra command requires shared secret parameter")
  void ocraCommandRequiresKey() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(new String[] {"ocra", "--suite=" + DEFAULT_SUITE}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("--key=<hex-shared-secret> is required"), harness.err());
  }

  @Test
  @DisplayName("ocra command handles help requests")
  void ocraCommandHandlesHelp() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"ocra", "--help"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("usage:"), harness.err());
  }

  @Test
  @DisplayName("ocra command handles short help flag")
  void ocraCommandHandlesShortHelp() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"ocra", "-h"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("usage:"), harness.err());
  }

  @Test
  @DisplayName("ocra command rejects unknown options")
  void ocraCommandRejectsUnknownOption() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {
              "ocra", "--suite=" + DEFAULT_SUITE, "--key=" + DEFAULT_SECRET_HEX, "--flag"
            },
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("unrecognised option"), harness.err());
  }

  @Test
  @DisplayName("ocra command rejects non-numeric counter values")
  void ocraCommandRejectsInvalidCounter() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {
              "ocra",
              "--suite=" + DEFAULT_SUITE,
              "--key=" + DEFAULT_SECRET_HEX,
              "--challenge=" + DEFAULT_CHALLENGE,
              "--counter=abc"
            },
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("counter must be a long value"), harness.err());
  }

  @Test
  @DisplayName("ocra command rejects blank suite parameter")
  void ocraCommandRejectsBlankSuite() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {"ocra", "--suite=   ", "--key=" + DEFAULT_SECRET_HEX},
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("--suite=<ocra-suite> is required"), harness.err());
  }

  @Test
  @DisplayName("ocra command rejects blank key parameter")
  void ocraCommandRejectsBlankKey() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {"ocra", "--suite=" + DEFAULT_SUITE, "--key=   "},
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("--key=<hex-shared-secret> is required"), harness.err());
  }

  @Test
  @DisplayName("ocra command trims optional inputs when blank")
  void ocraCommandTrimsOptionalInputs() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    String[] args = {
      "ocra",
      "--suite=" + DEFAULT_SUITE,
      "--key=" + DEFAULT_SECRET_HEX,
      "--challenge=   ",
      "--session=   ",
      "--client=   ",
      "--server=   ",
      "--pin=   ",
      "--timestamp=   "
    };

    MaintenanceCli.OcraArguments parsed = cli.parseOcraArguments(args, harness.err);

    assertTrue(parsed.challenge().isEmpty());
    assertTrue(parsed.sessionInformation().isEmpty());
    assertTrue(parsed.clientChallenge().isEmpty());
    assertTrue(parsed.serverChallenge().isEmpty());
    assertTrue(parsed.pinHashHex().isEmpty());
    assertTrue(parsed.timestampHex().isEmpty());
  }

  @Test
  @DisplayName("verify command reports status for populated database")
  void verifyCommandReportsStatus() throws Exception {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();
    Path tempDir = Files.createTempDirectory("maintenance-cli-verify");
    Path database = tempDir.resolve("store.db");

    importCredential(database, "cred-verify");

    int exitCode =
        cli.run(
            new String[] {"verify", "--database=" + database.toAbsolutePath()},
            harness.out,
            harness.err);

    String stdout = harness.out();
    assertEquals(0, exitCode, harness.err());
    assertTrue(stdout.contains("operation=INTEGRITY_CHECK"), stdout);
    assertTrue(stdout.contains("status="), stdout);

    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("verify command requires database path")
  void verifyCommandRequiresDatabase() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"verify"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("--database=<path> is required"), harness.err());
  }

  @Test
  @DisplayName("verify command rejects unknown options")
  void verifyCommandRejectsUnknownOptions() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode =
        cli.run(
            new String[] {"verify", "--database=/tmp/cli.db", "--flag"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("unrecognised option"), harness.err());
  }

  @Test
  @DisplayName("compact command handles help requests")
  void compactCommandHandlesHelp() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"compact", "--help"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("usage:"), harness.err());
  }

  @Test
  @DisplayName("verify command supports short database flag")
  void verifyCommandSupportsShortDatabaseFlag() throws Exception {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();
    Path tempDir = Files.createTempDirectory("maintenance-cli-verify-short");
    Path database = tempDir.resolve("store.db");

    importCredential(database, "short-verify");

    int exitCode =
        cli.run(
            new String[] {"verify", "-d=" + database.toAbsolutePath()}, harness.out, harness.err);

    assertEquals(0, exitCode, harness.err());
    deleteRecursively(tempDir);
  }

  @Test
  @DisplayName("verify command prints issues when migration path is missing")
  void verifyCommandPrintsIssuesForMissingMigrationPath() throws Exception {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();
    Path tempDir = Files.createTempDirectory("maintenance-cli-legacy");
    Path database = tempDir.resolve("store.db");

    MapDbMaintenanceFixtures.writeLegacyOcraRecordMissingSuite(
        database, "issue-fixture", SecretMaterial.fromHex(DEFAULT_SECRET_HEX));

    String propertyKey = "openauth.sim.persistence.skip-upgrade";
    String originalProperty = System.getProperty(propertyKey);
    System.setProperty(propertyKey, "true");

    try {
      int exitCode =
          cli.run(
              new String[] {"verify", "--database=" + database.toAbsolutePath()},
              harness.out,
              harness.err);

      String stdout = harness.out();
      assertEquals(0, exitCode, harness.err());
      assertTrue(stdout.contains("status=WARN"), stdout);
      assertTrue(stdout.contains("issues=1"), stdout);
      assertTrue(
          stdout.contains(
              "issue=issue-fixture:No migration path to latest schema for credential 'issue-fixture'"),
          stdout);
      assertTrue(harness.err().isBlank(), harness.err());
    } finally {
      deleteRecursively(tempDir);
      if (originalProperty == null) {
        System.clearProperty(propertyKey);
      } else {
        System.setProperty(propertyKey, originalProperty);
      }
    }
  }

  @Test
  @DisplayName("main executes maintenance command without exiting on success")
  void mainExecutesMaintenanceCommand() throws Exception {
    Path tempDir = Files.createTempDirectory("maintenance-cli-main");
    Path database = tempDir.resolve("store.db");
    importCredential(database, "main-cred");

    MaintenanceCli.main(new String[] {"compact", "--database=" + database.toAbsolutePath()});

    deleteRecursively(tempDir);
  }

  private static String extractField(String output, String field) {
    for (String token : output.split("\\s")) {
      if (token.startsWith(field + "=")) {
        return token.substring(field.length() + 1);
      }
    }
    return null;
  }

  private static final class OutputHarness {
    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    private final PrintStream out;
    private final PrintStream err;

    private OutputHarness() {
      out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
      err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
    }

    static OutputHarness create() {
      return new OutputHarness();
    }

    String out() {
      return outBuffer.toString(StandardCharsets.UTF_8);
    }

    String err() {
      return errBuffer.toString(StandardCharsets.UTF_8);
    }
  }

  private void importCredential(Path database, String credentialId) throws Exception {
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();

    OcraCredentialDescriptor descriptor =
        factory.createDescriptor(
            new OcraCredentialRequest(
                credentialId,
                DEFAULT_SUITE,
                DEFAULT_SECRET_HEX,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "maintenance-test")));

    VersionedCredentialRecord record = adapter.serialize(descriptor);
    Credential credential = VersionedCredentialRecordMapper.toCredential(record);

    try (MapDbCredentialStore store = ocraStoreBuilder(database).open()) {
      store.save(credential);
    }
  }

  private MapDbCredentialStore.Builder ocraStoreBuilder(Path database) {
    return OcraStoreMigrations.apply(MapDbCredentialStore.file(database));
  }

  private void deleteRecursively(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // best-effort cleanup for temp directories
                }
              });
    }
  }

  @Test
  @DisplayName("verify command reports failures for corrupted database")
  void verifyCommandReportsFailuresForCorruptStore() throws Exception {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();
    Path tempDir = Files.createTempDirectory("maintenance-cli-corrupt");
    Path database = tempDir.resolve("store.db");

    Files.write(database, new byte[] {0x01, 0x23, 0x45});

    int exitCode =
        cli.run(
            new String[] {"verify", "--database=" + database.toAbsolutePath()},
            harness.out,
            harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("error: maintenance command failed"), harness.err());
    assertTrue(harness.out().isBlank(), harness.out());

    deleteRecursively(tempDir);
  }

  private static String javaCommand() {
    Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
    return javaBin.toString();
  }

  private static String jacocoAgentArgument() {
    return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(arg -> arg.startsWith("-javaagent:"))
        .filter(arg -> arg.contains("jacocoagent"))
        .findFirst()
        .orElse(null);
  }
}
