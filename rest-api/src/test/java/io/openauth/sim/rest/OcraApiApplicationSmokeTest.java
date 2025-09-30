package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class OcraApiApplicationSmokeTest {

  @Test
  @DisplayName("application context boots and shuts down cleanly")
  void applicationBoots() {
    Path database = prepareTempDatabase();
    try (ConfigurableApplicationContext context =
        SpringApplication.run(
            RestApiApplication.class,
            new String[] {
              "--spring.main.web-application-type=none",
              "--spring.main.register-shutdown-hook=false",
              "--openauth.sim.persistence.database-path=" + database
            })) {
      assertTrue(context.isActive());
      assertTrue(context.containsBean("restApiApplication"));
    } finally {
      cleanupTempDatabase(database);
    }
  }

  @Test
  @DisplayName("main entry point starts without error")
  void mainRunsWithoutError() {
    Path database = prepareTempDatabase();
    assertDoesNotThrow(
        () ->
            RestApiApplication.main(
                new String[] {
                  "--spring.main.web-application-type=none",
                  "--spring.main.register-shutdown-hook=false",
                  "--openauth.sim.persistence.database-path=" + database
                }));
    cleanupTempDatabase(database);
  }

  private Path prepareTempDatabase() {
    try {
      Path tempDir = Files.createTempDirectory("rest-api-application-test");
      return tempDir.resolve("ocra-store.db");
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to prepare temporary database", ex);
    }
  }

  private void cleanupTempDatabase(Path database) {
    try {
      if (Files.exists(database)) {
        Files.deleteIfExists(database);
      }
      Path parent = database.getParent();
      if (parent != null && Files.exists(parent)) {
        Files.deleteIfExists(parent);
      }
    } catch (Exception ignored) {
      // best-effort cleanup
    }
  }
}
