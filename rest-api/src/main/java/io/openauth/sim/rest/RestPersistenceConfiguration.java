package io.openauth.sim.rest;

import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    return CredentialStoreFactory.openFileStore(resolvedPath);
  }

  static Path resolveDatabasePath(String configuredPath) {
    return CredentialStoreFactory.resolveDatabasePath(configuredPath, DEFAULT_DATABASE_FILE);
  }
}
