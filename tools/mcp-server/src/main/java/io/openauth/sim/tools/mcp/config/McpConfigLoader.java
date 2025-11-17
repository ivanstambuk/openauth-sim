package io.openauth.sim.tools.mcp.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class McpConfigLoader {

    private static final URI DEFAULT_BASE_URI = URI.create("http://localhost:8080");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    public static Path defaultConfigPath() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return Path.of("mcp-config.yaml");
        }
        return Path.of(home, ".config", "openauth-sim", "mcp-config.yaml");
    }

    public McpConfig load(Path rawPath) {
        Path path = rawPath == null ? defaultConfigPath() : rawPath;
        Map<String, String> values = readKeyValues(path);
        URI base = parseUri(values.getOrDefault("baseUrl", DEFAULT_BASE_URI.toString()));
        Optional<String> apiKey =
                Optional.ofNullable(values.get("apiKey")).map(String::trim).filter(s -> !s.isEmpty());
        Duration defaultTimeout = parseDuration(values.get("timeouts.defaultMillis"), DEFAULT_TIMEOUT);

        Map<String, Duration> toolTimeouts = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("timeouts.")) {
                String toolName = entry.getKey().substring("timeouts.".length());
                if (!"defaultMillis".equals(toolName)) {
                    toolTimeouts.put(toolName, parseDuration(entry.getValue(), DEFAULT_TIMEOUT));
                }
            }
        }

        return new McpConfig(base, apiKey, defaultTimeout, Map.copyOf(toolTimeouts));
    }

    private static Map<String, String> readKeyValues(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        if (path == null || !Files.exists(path)) {
            return values;
        }
        try {
            String currentSection = null;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                boolean indented = Character.isWhitespace(line.charAt(0));
                if (!indented) {
                    int colon = trimmed.indexOf(':');
                    if (colon < 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    if (value.isEmpty()) {
                        currentSection = key;
                    } else {
                        values.put(key, value);
                        currentSection = null;
                    }
                } else if (currentSection != null) {
                    int colon = trimmed.indexOf(':');
                    if (colon < 0) {
                        continue;
                    }
                    String nestedKey = trimmed.substring(0, colon).trim();
                    String nestedValue = trimmed.substring(colon + 1).trim();
                    values.put(currentSection + "." + nestedKey, nestedValue);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read config file " + path, ex);
        }
        return values;
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid baseUrl: " + value, ex);
        }
    }

    private static Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long millis = Long.parseLong(value.trim());
            return Duration.ofMillis(millis);
        } catch (NumberFormatException ex) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.endsWith("s")) {
                String seconds = lower.substring(0, lower.length() - 1).trim();
                return Duration.ofSeconds(Long.parseLong(seconds));
            }
            throw new IllegalStateException("Invalid duration value: " + value, ex);
        }
    }
}
