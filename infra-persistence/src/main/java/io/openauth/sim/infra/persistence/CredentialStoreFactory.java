package io.openauth.sim.infra.persistence;

import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.ocra.OcraStoreMigrations;
import io.openauth.sim.core.support.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/** Central factory for MapDB-backed credential stores used by CLI, REST, and tests. */
public final class CredentialStoreFactory {

  private static final Logger LOGGER =
      Logger.getLogger("io.openauth.sim.infra.persistence.CredentialStoreFactory");
  private static final String UNIFIED_DEFAULT_FILENAME = "credentials.db";
  private static final List<String> LEGACY_DEFAULT_FILENAMES =
      List.of(
          "ocra-credentials.db",
          "totp-credentials.db",
          "hotp-credentials.db",
          "fido2-credentials.db");

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

    Path unifiedPath = ProjectPaths.resolveDataFile(UNIFIED_DEFAULT_FILENAME);
    if (Files.exists(unifiedPath)) {
      return unifiedPath;
    }

    for (String legacyName : LEGACY_DEFAULT_FILENAMES) {
      Path legacyPath = ProjectPaths.resolveDataFile(legacyName);
      if (Files.exists(legacyPath)) {
        logLegacyFallback(legacyName, legacyPath);
        return legacyPath;
      }
    }

    if (!UNIFIED_DEFAULT_FILENAME.equals(defaultFileName)) {
      Path legacyDefault = ProjectPaths.resolveDataFile(defaultFileName);
      if (Files.exists(legacyDefault)) {
        logLegacyFallback(defaultFileName, legacyDefault);
        return legacyDefault;
      }
    }

    return unifiedPath;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static void logLegacyFallback(String fileName, Path resolvedPath) {
    LOGGER.info(
        () ->
            "Using legacy credential store file '"
                + fileName
                + "' located at "
                + resolvedPath.toAbsolutePath());
  }
}
