package io.openauth.sim.rest;

import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
class RestPersistenceConfiguration {

  private static final String DEFAULT_DATABASE_FILE = "ocra-credentials.db";

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(CredentialStore.class)
  @ConditionalOnProperty(
      name = "openauth.sim.persistence.enable-store",
      havingValue = "true",
      matchIfMissing = true)
  MapDbCredentialStore credentialStore(
      @Value("${openauth.sim.persistence.database-path:}") String databasePath) throws IOException {
    Path resolvedPath = resolveDatabasePath(databasePath);
    ensureParentDirectory(resolvedPath);
    return MapDbCredentialStore.file(resolvedPath).open();
  }

  private static Path resolveDatabasePath(String configuredPath) {
    if (StringUtils.hasText(configuredPath)) {
      return Paths.get(configuredPath).toAbsolutePath();
    }
    return ProjectPaths.resolveDataFile(DEFAULT_DATABASE_FILE);
  }

  private static void ensureParentDirectory(Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
