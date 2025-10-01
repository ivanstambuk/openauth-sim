package io.openauth.sim.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class ReflectionRegexScanTest {

  private static final List<String> MODULES = List.of("core", "cli", "rest-api", "ui");

  @Test
  @DisplayName("Source tree should be free of reflection tokens")
  void reflectionTokensShouldNotAppearInSources() throws IOException {
    Path projectRoot = Paths.get("..").toAbsolutePath().normalize();
    List<String> offenders = new ArrayList<>();

    List<String> reflectionTokens = loadReflectionTokens(projectRoot);

    for (String module : MODULES) {
      Path moduleSrc = projectRoot.resolve(module).resolve("src");
      if (!Files.isDirectory(moduleSrc)) {
        continue;
      }

      Files.walk(moduleSrc)
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .forEach(path -> collectOffenders(path, reflectionTokens, offenders));
    }

    Assertions.assertTrue(
        offenders.isEmpty(),
        () ->
            "Reflection tokens present in sources:\n"
                + String.join(System.lineSeparator(), offenders));
  }

  private static void collectOffenders(
      Path path, List<String> reflectionTokens, List<String> offenders) {
    String content;
    try {
      content = Files.readString(path);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read " + path, ex);
    }

    for (String token : reflectionTokens) {
      if (content.contains(token)) {
        offenders.add(path.toString() + " -> " + token);
      }
    }
  }

  private static List<String> loadReflectionTokens(Path projectRoot) {
    Path tokenFile = projectRoot.resolve("config/reflection-tokens.txt");
    try {
      return Files.readAllLines(tokenFile).stream()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(Collectors.toList());
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read reflection token file " + tokenFile, ex);
    }
  }
}
