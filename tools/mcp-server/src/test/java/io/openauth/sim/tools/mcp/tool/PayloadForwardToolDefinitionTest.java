package io.openauth.sim.tools.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.tools.mcp.config.McpConfig;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PayloadForwardToolDefinitionTest {

    @Test
    void buildsPostRequestWithAuthorizationHeader() {
        RestToolDefinition definition =
                new PayloadForwardToolDefinition("hotp.evaluate", "Evaluate HOTP", "POST", "/api/v1/hotp/evaluate");
        McpConfig config = new McpConfig(
                URI.create("http://localhost:8080"), Optional.of("abc123"), Duration.ofSeconds(2), Map.of());
        RestToolInvocation invocation = definition.createInvocation(config, Map.of("payload", "{}"));
        HttpRequest request = invocation.request();
        assertEquals("http://localhost:8080/api/v1/hotp/evaluate", request.uri().toString());
        assertTrue(request.headers().firstValue("Authorization").isPresent());
        assertEquals(
                "Bearer abc123", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("POST", request.method());
    }

    @Test
    void rejectsMissingPayload() {
        RestToolDefinition definition =
                new PayloadForwardToolDefinition("totp.evaluate", "Evaluate TOTP", "POST", "/api/v1/totp/evaluate");
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(2), Map.of());
        assertThrows(IllegalArgumentException.class, () -> definition.createInvocation(config, Map.of()));
    }

    @Test
    void descriptorIncludesSchemaPromptHintsAndVersion() {
        RestToolDefinition definition =
                new PayloadForwardToolDefinition("totp.evaluate", "Evaluate TOTP", "POST", "/api/v1/totp/evaluate");
        Map<String, Object> descriptor = definition.descriptor();
        assertEquals("totp.evaluate", descriptor.get("name"));
        assertEquals("Evaluate TOTP", descriptor.get("description"));
        Object inputSchema = descriptor.get("inputSchema");
        Object schema = descriptor.get("schema");
        assertNotNull(inputSchema);
        assertTrue(inputSchema instanceof Map);
        assertEquals(inputSchema, schema);
        @SuppressWarnings("unchecked")
        List<String> promptHints = (List<String>) descriptor.get("promptHints");
        assertNotNull(promptHints);
        assertTrue(promptHints.size() >= 1);
        assertEquals(McpToolMetadata.catalogVersion(), descriptor.get("version"));
    }
}
