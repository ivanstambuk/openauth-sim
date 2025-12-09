package io.openauth.sim.application.eudi.openid4vp.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    static String readUtf8(String classpathPath, Path filesystemPath) {
        String value = readUtf8OrNull(classpathPath, filesystemPath);
        if (value == null) {
            throw new IllegalStateException(
                    "Unable to load fixture resource from classpath '" + classpathPath + "' or path " + filesystemPath);
        }
        return value;
    }

    static String readUtf8OrNull(String classpathPath, Path filesystemPath) {
        String fromClasspath = readUtf8FromClasspath(classpathPath);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        try {
            if (Files.exists(filesystemPath)) {
                return Files.readString(filesystemPath, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static String readUtf8FromClasspath(String classpathPath) {
        for (ClassLoader loader : classLoaders()) {
            for (String candidate : List.of(classpathPath, "/" + classpathPath)) {
                try (InputStream stream = loader.getResourceAsStream(candidate)) {
                    if (stream == null) {
                        continue;
                    }
                    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        java.io.StringWriter writer = new java.io.StringWriter();
                        reader.transferTo(writer);
                        return writer.toString();
                    }
                } catch (IOException ex) {
                    continue;
                }
            }
        }
        return null;
    }

    private static List<ClassLoader> classLoaders() {
        return List.of(
                FixturePaths.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader());
    }
}
