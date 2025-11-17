package io.openauth.sim.tools.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.tools.mcp.config.McpConfig;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class FixturesListToolDefinitionTest {

    @Test
    void buildsGetRequestForHotp() {
        FixturesListToolDefinition definition =
                new FixturesListToolDefinition(Map.of("hotp", "/api/v1/hotp/credentials"));
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(5), Map.of());
        RestToolInvocation invocation = definition.createInvocation(config, Map.of("protocol", "hotp"));
        HttpRequest request = invocation.request();
        assertEquals(
                "http://localhost:8080/api/v1/hotp/credentials", request.uri().toString());
        assertEquals("GET", request.method());
    }

    @Test
    void rejectsUnknownProtocol() {
        FixturesListToolDefinition definition =
                new FixturesListToolDefinition(Map.of("hotp", "/api/v1/hotp/credentials"));
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(5), Map.of());
        assertThrows(
                IllegalArgumentException.class, () -> definition.createInvocation(config, Map.of("protocol", "bad")));
    }
}
