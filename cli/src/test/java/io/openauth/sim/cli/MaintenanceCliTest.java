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
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
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
  @SuppressWarnings("unchecked")
  void parseOcraArgumentsCapturesOptionalParameters() throws Exception {
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

    Method method =
        MaintenanceCli.class.getDeclaredMethod(
            "parseOcraArguments", String[].class, PrintStream.class);
    method.setAccessible(true);

    Object parsed = method.invoke(cli, new Object[] {args, harness.err});

    Method sessionMethod = parsed.getClass().getDeclaredMethod("sessionInformation");
    Method clientMethod = parsed.getClass().getDeclaredMethod("clientChallenge");
    Method serverMethod = parsed.getClass().getDeclaredMethod("serverChallenge");
    Method pinMethod = parsed.getClass().getDeclaredMethod("pinHashHex");
    Method timestampMethod = parsed.getClass().getDeclaredMethod("timestampHex");
    Method counterMethod = parsed.getClass().getDeclaredMethod("counter");

    Optional<String> session = (Optional<String>) sessionMethod.invoke(parsed);
    Optional<String> client = (Optional<String>) clientMethod.invoke(parsed);
    Optional<String> server = (Optional<String>) serverMethod.invoke(parsed);
    Optional<String> pin = (Optional<String>) pinMethod.invoke(parsed);
    Optional<String> timestamp = (Optional<String>) timestampMethod.invoke(parsed);
    Optional<Long> counter = (Optional<Long>) counterMethod.invoke(parsed);

    assertTrue(session.isPresent());
    assertTrue(client.isPresent());
    assertTrue(server.isPresent());
    assertTrue(pin.isPresent());
    assertTrue(timestamp.isPresent());
    assertTrue(counter.isPresent());
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
  @DisplayName("ocra command handles help requests")
  void ocraCommandHandlesHelp() {
    MaintenanceCli cli = new MaintenanceCli();
    OutputHarness harness = OutputHarness.create();

    int exitCode = cli.run(new String[] {"ocra", "--help"}, harness.out, harness.err);

    assertEquals(1, exitCode);
    assertTrue(harness.err().contains("usage:"), harness.err());
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

    try (MapDbCredentialStore store = MapDbCredentialStore.file(database).open()) {
      store.save(credential);
    }
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
}
