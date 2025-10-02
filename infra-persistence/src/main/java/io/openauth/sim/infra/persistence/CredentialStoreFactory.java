package io.openauth.sim.infra.persistence;

import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import io.openauth.sim.core.support.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Central factory for MapDB-backed credential stores used by CLI, REST, and tests. */
public final class CredentialStoreFactory {

  private CredentialStoreFactory() {
    throw new AssertionError("No instances");
  }

  public static MapDbCredentialStore openFileStore(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    Path absolute = path.toAbsolutePath();
    Path parent = absolute.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    return OcraStoreMigrations.apply(MapDbCredentialStore.file(absolute)).open();
  }

  public static MapDbCredentialStore openInMemoryStore() {
    return OcraStoreMigrations.apply(MapDbCredentialStore.inMemory()).open();
  }

  public static Path resolveDatabasePath(String configuredPath, String defaultFileName) {
    if (hasText(configuredPath)) {
      return Paths.get(configuredPath.trim()).toAbsolutePath();
    }
    Objects.requireNonNull(defaultFileName, "defaultFileName");
    return ProjectPaths.resolveDataFile(defaultFileName);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
