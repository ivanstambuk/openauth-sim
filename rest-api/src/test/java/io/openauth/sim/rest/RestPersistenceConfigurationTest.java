package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.store.CredentialStore;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    Path resolved = invokeResolveDatabasePath("   ");
    assertTrue(resolved.toString().endsWith("ocra-credentials.db"));
  }

  @Test
  @DisplayName("resolveDatabasePath honors configured absolute path")
  void resolveDatabasePathHonorsConfiguredPath() throws Exception {
    Path configured = Paths.get("build", "custom", "store.db").toAbsolutePath();
    Path resolved = invokeResolveDatabasePath(configured.toString());
    assertEquals(configured, resolved);
  }

  @Test
  @DisplayName("ensureParentDirectory is a no-op when parent is null")
  void ensureParentDirectoryHandlesNullParent() throws Exception {
    Path singleFile = Paths.get("store.db").toAbsolutePath().getFileName();
    invokeEnsureParentDirectory(singleFile);
  }

  private static Path invokeResolveDatabasePath(String input)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method =
        RestPersistenceConfiguration.class.getDeclaredMethod("resolveDatabasePath", String.class);
    method.setAccessible(true);
    return (Path) method.invoke(null, input);
  }

  private static void invokeEnsureParentDirectory(Path path)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
    Method method =
        RestPersistenceConfiguration.class.getDeclaredMethod("ensureParentDirectory", Path.class);
    method.setAccessible(true);
    try {
      method.invoke(null, path);
    } catch (InvocationTargetException ex) {
      throw ex;
    }
  }
}
