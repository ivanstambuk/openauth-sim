package io.openauth.sim.tools.mcp.tool;

import io.openauth.sim.tools.mcp.config.McpConfig;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PayloadForwardToolDefinition implements RestToolDefinition {
    private final String name;
    private final String description;
    private final String method;
    private final String path;

    PayloadForwardToolDefinition(String name, String description, String method, String path) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.method = Objects.requireNonNull(method, "method").toUpperCase();
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Map<String, Object> descriptor() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "payload",
                Map.of(
                        "type", "string",
                        "description", "Raw JSON body forwarded to the REST endpoint"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("payload"));
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", name);
        descriptor.put("description", description);
        descriptor.put("inputSchema", schema);
        descriptor.put("schema", schema);
        List<String> promptHints = McpToolMetadata.promptHints(name);
        if (!promptHints.isEmpty()) {
            descriptor.put("promptHints", promptHints);
        }
        descriptor.put("version", McpToolMetadata.catalogVersion());
        return descriptor;
    }

    @Override
    public RestToolInvocation createInvocation(McpConfig config, Map<String, Object> arguments) {
        String payload = readPayload(arguments);
        URI uri = config.baseUrl().resolve(path);
        Duration timeout = config.timeoutFor(name);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        config.apiKey().ifPresent(key -> builder.header("Authorization", "Bearer " + key));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        } else if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }
        return new RestToolInvocation(name, builder.build());
    }

    private static String readPayload(Map<String, Object> arguments) {
        Object value = arguments == null ? null : arguments.get("payload");
        if (value == null) {
            throw new IllegalArgumentException("Tool call requires a payload argument");
        }
        String payload = value.toString().trim();
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("Payload argument must not be blank");
        }
        return payload;
    }
}
