package io.openauth.sim.application.eudi.openid4vp.fixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class FixturePaths {
    private static final List<Path> ROOTS = List.of(Path.of("."), Path.of(".."), Path.of("..", ".."));

    private FixturePaths() {}

    static Path resolve(String first, String... more) {
        Path relative = Path.of(first, more);
        for (Path root : ROOTS) {
            Path candidate = root.resolve(relative).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return relative.normalize();
    }
}
