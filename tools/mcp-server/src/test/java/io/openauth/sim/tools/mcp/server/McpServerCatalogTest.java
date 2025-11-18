package io.openauth.sim.tools.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.tools.mcp.config.McpConfig;
import io.openauth.sim.tools.mcp.http.HttpExecutor;
import io.openauth.sim.tools.mcp.json.Json;
import io.openauth.sim.tools.mcp.tool.RestToolRegistry;
import io.openauth.sim.tools.mcp.transport.ContentLengthMessageFramer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

final class McpServerCatalogTest {

    @Test
    void toolsListIncludesSchemasPromptHintsVersionAndTelemetry() throws IOException {
        String listRequest = frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        ByteArrayInputStream input =
                new ByteArrayInputStream(listRequest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpExecutor executor = request -> new NoOpHttpResponse(request);
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(5), Map.of());
        McpServer server = new McpServer(config, RestToolRegistry.defaultRegistry(), executor, input, output);
        server.run();

        ContentLengthMessageFramer reader = new ContentLengthMessageFramer(
                new ByteArrayInputStream(output.toByteArray()), new ByteArrayOutputStream());
        Optional<String> responseJson = reader.read();
        assertTrue(responseJson.isPresent());

        Map<String, Object> response = Json.expectObject(Json.parse(responseJson.get()), "tools/list response");
        Map<String, Object> result = Json.expectObject(response.get("result"), "tools/list result");
        Object rawTools = result.get("tools");
        assertNotNull(rawTools);
        assertTrue(rawTools instanceof List<?>);
        List<?> tools = (List<?>) rawTools;
        assertFalse(tools.isEmpty());

        for (Object rawTool : tools) {
            Map<String, Object> tool = Json.expectObject(rawTool, "tool descriptor");
            assertNotNull(tool.get("name"));
            assertNotNull(tool.get("description"));
            Object inputSchema = tool.get("inputSchema");
            Object schema = tool.get("schema");
            assertNotNull(inputSchema);
            assertEquals(inputSchema, schema);
            Object promptHints = tool.get("promptHints");
            assertNotNull(promptHints);
            assertTrue(promptHints instanceof List<?>);
            assertNotNull(tool.get("version"));
        }

        Map<String, Object> telemetry = Json.expectObject(result.get("telemetry"), "catalog telemetry");
        assertEquals("mcp.catalog.listed", telemetry.get("event"));
        assertTrue(telemetry.get("telemetryId") instanceof String);
        assertTrue(telemetry.get("schemaHash") instanceof String);
        Object toolCount = telemetry.get("toolCount");
        assertTrue(toolCount instanceof Number);
        assertEquals(((Number) toolCount).intValue(), tools.size());
        assertEquals(Boolean.TRUE, telemetry.get("sanitized"));
    }

    private static String frame(String json) {
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return "Content-Length: " + bytes.length + "\r\n\r\n" + json;
    }

    private static final class NoOpHttpResponse implements HttpResponse<String> {
        private final HttpRequest request;

        private NoOpHttpResponse(HttpRequest request) {
            this.request = request;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(Map.of(), (k, v) -> true);
        }

        @Override
        public String body() {
            return "{\"status\":\"ok\"}";
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
