package io.openauth.sim.core.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Utility helpers for resolving repository-relative paths. */
public final class ProjectPaths {

    private static final List<String> ROOT_MARKERS = List.of("settings.gradle.kts", "settings.gradle");

    private ProjectPaths() {
        // Utility class
    }

    /** Locate the repository root by walking up from the supplied starting path. */
    public static Path findRepositoryRoot(Path start) {
        Path current = start.toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            for (String marker : ROOT_MARKERS) {
                if (Files.exists(current.resolve(marker))) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return start.toAbsolutePath();
    }

    /** Resolve a file inside the shared `data/` directory at the repository root. */
    public static Path resolveDataFile(String fileName) {
        Path root = findRepositoryRoot(Paths.get(""));
        return root.resolve("data").resolve(fileName).toAbsolutePath();
    }
}
