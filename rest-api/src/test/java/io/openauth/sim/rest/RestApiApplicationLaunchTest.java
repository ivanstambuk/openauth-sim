package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

final class RestApiApplicationLaunchTest {

    @Test
    @DisplayName("launch returns an active context and preserves database path override")
    void launchReturnsContextWithDatabaseOverride() throws Exception {
        Path tempDir = Files.createTempDirectory("rest-api-launch-test");
        Path databasePath = tempDir.resolve("launch-store.db");

        String[] args = {
            "--spring.main.web-application-type=none",
            "--spring.main.register-shutdown-hook=false",
            "--openauth.sim.persistence.database-path=" + databasePath
        };

        try (ConfigurableApplicationContext context = RestApiApplication.launch(args)) {
            assertTrue(context.isActive());
            assertTrue(context.containsBean("restApiApplication"));
            String configuredPath = Objects.requireNonNull(
                    context.getEnvironment().getProperty("openauth.sim.persistence.database-path"));
            assertEquals(databasePath.toString(), configuredPath);
        } finally {
            cleanupDirectory(tempDir);
        }
    }

    private void cleanupDirectory(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort cleanup for temporary directories
                }
            });
        }
    }
}
