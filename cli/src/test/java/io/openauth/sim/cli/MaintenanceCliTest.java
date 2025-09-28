package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.MapDbCredentialStore;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaintenanceCliTest {

  @TempDir Path tempDir;

  @Test
  void compactAgainstFileStoreProducesSuccessTelemetry() throws Exception {
    Path dbPath = tempDir.resolve("persistence.db");
    try (MapDbCredentialStore store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(
          Credential.create(
              "cli-maint", CredentialType.GENERIC, SecretMaterial.fromHex("00112233"), Map.of()));
    }

    MaintenanceCli cli = new MaintenanceCli();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {"compact", "--database=" + dbPath},
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode, "compact should exit successfully");
    String output = stdout.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("operation=COMPACTION"));
    assertTrue(output.contains("status=SUCCESS"));
    assertTrue(output.contains("entriesScanned=1"));
    assertTrue(output.contains("entriesRepaired=0"));
    assertTrue(output.contains("issues=0"));
    assertEquals("", stderr.toString(StandardCharsets.UTF_8));
  }

  @Test
  void verifyIntegrityReportsSuccess() throws Exception {
    Path dbPath = tempDir.resolve("integrity.db");
    try (MapDbCredentialStore store = MapDbCredentialStore.file(dbPath).open()) {
      store.save(
          Credential.create(
              "cli-verify",
              CredentialType.GENERIC,
              SecretMaterial.fromHex("aabbccdd"),
              Map.of("note", "verify")));
    }

    MaintenanceCli cli = new MaintenanceCli();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {"verify", "--database=" + dbPath},
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode, "verify should exit successfully");
    String output = stdout.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("operation=INTEGRITY_CHECK"));
    assertTrue(output.contains("status=SUCCESS"));
    assertTrue(output.contains("issues=0"));
    assertEquals("", stderr.toString(StandardCharsets.UTF_8));
  }

  @Test
  void missingArgumentsReturnUsageError() {
    MaintenanceCli cli = new MaintenanceCli();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {},
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode, "missing args should fail");
    String errorOutput = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(errorOutput.contains("usage"));
  }
}
