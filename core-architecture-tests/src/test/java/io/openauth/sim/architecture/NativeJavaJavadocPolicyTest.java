package io.openauth.sim.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
final class NativeJavaJavadocPolicyTest {

    private static final List<Path> SOURCE_ROOTS = List.of(
            Paths.get("..", "core", "src", "main", "java"), Paths.get("..", "application", "src", "main", "java"));

    private static final char NO_BREAK_SPACE = '\u00A0';
    private static final char NARROW_NO_BREAK_SPACE = '\u202F';

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("Feature\\s+\\d+"),
            Pattern.compile("FR-\\d{3}"),
            Pattern.compile("NFR-\\d{3}"),
            Pattern.compile("T\\d{4,}"));

    @Test
    @DisplayName("Native Java Javadoc omits internal roadmap identifiers")
    void javadocOmitsInternalIdentifiers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collectViolations(path, violations));
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Public Javadoc must not leak internal identifiers (Feature/FR/NFR/T):\n"
                        + violations.stream().collect(Collectors.joining("\n")));
    }

    private static void collectViolations(Path path, List<String> violations) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String block : extractJavadocBlocks(content)) {
                String normalized = normalizeWhitespace(block);
                for (Pattern pattern : FORBIDDEN_PATTERNS) {
                    Matcher matcher = pattern.matcher(normalized);
                    if (matcher.find()) {
                        violations.add(path + ": " + matcher.group());
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read file: " + path, e);
        }
    }

    private static String normalizeWhitespace(String block) {
        return block.replace(NO_BREAK_SPACE, ' ').replace(NARROW_NO_BREAK_SPACE, ' ');
    }

    private static List<String> extractJavadocBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        Pattern pattern = Pattern.compile("/\\*\\*(?:.|\\R)*?\\*/", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            blocks.add(matcher.group());
        }
        return blocks;
    }
}
