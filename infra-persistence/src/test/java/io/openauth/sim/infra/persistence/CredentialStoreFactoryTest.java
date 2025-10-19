package io.openauth.sim.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class CredentialStoreFactoryTest {

    @Test
    @DisplayName("openFileStore creates parent directories")
    void openFileStoreCreatesParentDirectories() throws Exception {
        Path tempDir = Files.createTempDirectory("credential-store-factory");
        Path database = tempDir.resolve("nested/store.db");

        try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
            assertNotNull(store);
            assertTrue(Files.exists(database.getParent()));
        }
    }

    @Test
    @DisplayName("resolveDatabasePath falls back to unified default file")
    void resolveDatabasePathFallsBackToDefault() {
        Path resolved = CredentialStoreFactory.resolveDatabasePath("   ", "legacy.db");
        assertEquals(ProjectPaths.resolveDataFile("credentials.db"), resolved);
    }

    @Test
    @DisplayName("resolveDatabasePath honors configured path")
    void resolveDatabasePathHonorsConfiguredPath() {
        Path configured = Path.of("build", "custom", "store.db").toAbsolutePath();
        Path resolved = CredentialStoreFactory.resolveDatabasePath(configured.toString(), "ignored.db");
        assertEquals(configured, resolved);
    }
}
