package io.openauth.sim.tools.mcp.tool;

import io.openauth.sim.tools.mcp.config.McpConfig;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class FixturesListToolDefinition implements RestToolDefinition {
    private final Map<String, String> protocolPaths;

    FixturesListToolDefinition(Map<String, String> protocolPaths) {
        this.protocolPaths = Map.copyOf(protocolPaths);
    }

    @Override
    public String name() {
        return "fixtures.list";
    }

    @Override
    public String description() {
        return "Lists stored credential presets for a protocol";
    }

    @Override
    public Map<String, Object> descriptor() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "protocol",
                Map.of(
                        "type", "string",
                        "description", "Protocol alias (hotp, totp, ocra, emv, fido2, eudiw)",
                        "enum", protocolPaths.keySet()));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("protocol"));
        return Map.of("name", name(), "description", description(), "inputSchema", schema);
    }

    @Override
    public RestToolInvocation createInvocation(McpConfig config, Map<String, Object> arguments) {
        String protocol = readProtocol(arguments);
        String path = protocolPaths.get(protocol);
        URI uri = config.baseUrl().resolve(path);
        Duration timeout = config.timeoutFor(name());
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json");
        config.apiKey().ifPresent(key -> builder.header("Authorization", "Bearer " + key));
        builder.GET();
        return new RestToolInvocation(name(), builder.build());
    }

    private String readProtocol(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("protocol");
        if (raw == null) {
            throw new IllegalArgumentException("fixtures.list requires a protocol argument");
        }
        String value = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (!protocolPaths.containsKey(value)) {
            throw new IllegalArgumentException("Unsupported protocol: " + raw);
        }
        return value;
    }
}
