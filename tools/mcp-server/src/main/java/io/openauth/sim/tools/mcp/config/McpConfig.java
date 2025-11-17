package io.openauth.sim.tools.mcp.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record McpConfig(
        URI baseUrl, Optional<String> apiKey, Duration defaultTimeout, Map<String, Duration> toolTimeouts) {

    public McpConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(defaultTimeout, "defaultTimeout");
        Objects.requireNonNull(toolTimeouts, "toolTimeouts");
    }

    public Duration timeoutFor(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return defaultTimeout;
        }
        return toolTimeouts.getOrDefault(toolName, defaultTimeout);
    }
}
