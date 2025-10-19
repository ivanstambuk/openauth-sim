package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RestPersistenceConfigurationTest {

  private final RestPersistenceConfiguration configuration = new RestPersistenceConfiguration();

  @Test
  @DisplayName("credentialStore resolves configured path and ensures parent directory")
  void credentialStoreResolvesConfiguredPath() throws Exception {
    Path tempDir = Files.createTempDirectory("rest-persistence-config");
    Path database = tempDir.resolve("nested/store.db");

    try (CredentialStore store = configuration.credentialStore(database.toString())) {
      assertNotNull(store);
      assertTrue(Files.exists(database.getParent()));
    }
  }

  @Test
  @DisplayName("resolveDatabasePath falls back to default when blank")
  void resolveDatabasePathFallsBackToDefault() throws Exception {
    Path resolved = RestPersistenceConfiguration.resolveDatabasePath("   ");
    assertTrue(resolved.toString().endsWith("credentials.db"));
  }

  @Test
  @DisplayName("resolveDatabasePath ignores legacy store when present")
  void resolveDatabasePathIgnoresLegacyStore() throws Exception {
    Path unified = ProjectPaths.resolveDataFile("credentials.db");
    Path legacy = unified.getParent().resolve("test-legacy-store.db");
    Files.createDirectories(legacy.getParent());
    Files.deleteIfExists(legacy);
    Files.createFile(legacy);
    try {
      Path resolved = RestPersistenceConfiguration.resolveDatabasePath("   ");
      assertEquals(unified, resolved);
    } finally {
      Files.deleteIfExists(legacy);
    }
  }

  @Test
  @DisplayName("resolveDatabasePath honors configured absolute path")
  void resolveDatabasePathHonorsConfiguredPath() throws Exception {
    Path configured = Paths.get("build", "custom", "store.db").toAbsolutePath();
    Path resolved = RestPersistenceConfiguration.resolveDatabasePath(configured.toString());
    assertEquals(configured, resolved);
  }
}
