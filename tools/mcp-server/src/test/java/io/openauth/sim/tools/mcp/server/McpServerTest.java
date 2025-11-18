package io.openauth.sim.tools.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

final class McpServerTest {

    @Test
    void handlesInitializeAndToolCall() throws IOException {
        String initialize = frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        String call = frame(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"hotp.evaluate\",\"arguments\":{\"payload\":\"{}\"}}}");
        ByteArrayInputStream input =
                new ByteArrayInputStream((initialize + call).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpExecutor executor = request -> new TestHttpResponse(request, 200, "{\"status\":\"ok\"}");
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(5), Map.of());
        McpServer server = new McpServer(config, RestToolRegistry.defaultRegistry(), executor, input, output);
        server.run();

        ContentLengthMessageFramer reader = new ContentLengthMessageFramer(
                new ByteArrayInputStream(output.toByteArray()), new ByteArrayOutputStream());
        assertTrue(reader.read().isPresent()); // initialize response
        assertTrue(reader.read().isPresent()); // notification
        Optional<String> toolResponse = reader.read();
        assertTrue(toolResponse.isPresent());
        assertTrue(toolResponse.get().contains("\"statusCode\":200"));
    }

    @Test
    void includesTelemetryForTotpHelper() throws IOException {
        String initialize = frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        String call = frame(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"totp.helper.currentOtp\",\"arguments\":{\"payload\":\"{\\\"credentialId\\\":\\\"helper-demo\\\",\\\"timestamp\\\":59}\"}}}");
        ByteArrayInputStream input =
                new ByteArrayInputStream((initialize + call).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String helperBody = "{\"credentialId\":\"helper-demo\",\"otp\":\"94287082\",\"generationEpochSeconds\":59,"
                + "\"expiresEpochSeconds\":89,\"metadata\":{\"telemetryId\":\"rest-totp-helper-demo\"}}";
        HttpExecutor executor = request -> new TestHttpResponse(request, 200, helperBody);
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(5), Map.of());
        McpServer server = new McpServer(config, RestToolRegistry.defaultRegistry(), executor, input, output);
        server.run();

        ContentLengthMessageFramer reader = new ContentLengthMessageFramer(
                new ByteArrayInputStream(output.toByteArray()), new ByteArrayOutputStream());
        assertTrue(reader.read().isPresent()); // initialize response
        assertTrue(reader.read().isPresent()); // notification
        Optional<String> toolResponse = reader.read();
        assertTrue(toolResponse.isPresent());

        Map<String, Object> envelope =
                Json.expectObject(Json.parse(toolResponse.get()), "totp.helper.currentOtp response");
        Map<String, Object> result = Json.expectObject(envelope.get("result"), "tool result");
        assertEquals("totp.helper.currentOtp", result.get("tool"));
        @SuppressWarnings("unchecked")
        Map<String, Object> telemetry = (Map<String, Object>) result.get("telemetry");
        assertNotNull(telemetry);
        assertEquals("mcp.totp.helper.lookup", telemetry.get("event"));
        assertEquals("rest-totp-helper-demo", telemetry.get("telemetryId"));
        assertEquals(Boolean.TRUE, telemetry.get("sanitized"));
        Object credentialIdHash = telemetry.get("credentialId");
        assertTrue(credentialIdHash instanceof String);
        String credentialId = (String) credentialIdHash;
        assertTrue(credentialId.startsWith("sha256:"));
        assertTrue(credentialId.length() > "sha256:".length());
        assertTrue(telemetry.get("generationEpochSeconds") instanceof Number);
        assertEquals(59L, ((Number) telemetry.get("generationEpochSeconds")).longValue());
    }

    @Test
    void includesTelemetryForFixturesList() throws IOException {
        String initialize = frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        String call = frame(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"fixtures.list\",\"arguments\":{\"protocol\":\"hotp\"}}}");
        ByteArrayInputStream input =
                new ByteArrayInputStream((initialize + call).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String fixturesBody = "[{\"id\":\"ui-hotp-demo\",\"label\":\"Demo HOTP preset\",\"digits\":6}]";
        HttpExecutor executor = request -> new TestHttpResponse(request, 200, fixturesBody);
        McpConfig config =
                new McpConfig(URI.create("http://localhost:8080"), Optional.empty(), Duration.ofSeconds(5), Map.of());
        McpServer server = new McpServer(config, RestToolRegistry.defaultRegistry(), executor, input, output);
        server.run();

        ContentLengthMessageFramer reader = new ContentLengthMessageFramer(
                new ByteArrayInputStream(output.toByteArray()), new ByteArrayOutputStream());
        assertTrue(reader.read().isPresent()); // initialize response
        assertTrue(reader.read().isPresent()); // notification
        Optional<String> toolResponse = reader.read();
        assertTrue(toolResponse.isPresent());

        Map<String, Object> envelope = Json.expectObject(Json.parse(toolResponse.get()), "fixtures.list response");
        Map<String, Object> result = Json.expectObject(envelope.get("result"), "tool result");
        assertEquals("fixtures.list", result.get("tool"));
        @SuppressWarnings("unchecked")
        Map<String, Object> telemetry = (Map<String, Object>) result.get("telemetry");
        assertNotNull(telemetry);
        assertEquals("mcp.fixtures.list", telemetry.get("event"));
        assertEquals(Boolean.TRUE, telemetry.get("sanitized"));
        assertEquals("hotp", telemetry.get("protocol"));
        assertTrue(telemetry.get("presetCount") instanceof Number);
        assertEquals(1, ((Number) telemetry.get("presetCount")).intValue());
        @SuppressWarnings("unchecked")
        List<String> presetIds = (List<String>) telemetry.get("presetIds");
        assertNotNull(presetIds);
        assertEquals(1, presetIds.size());
        assertEquals("ui-hotp-demo", presetIds.get(0));
    }

    private static String frame(String json) {
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return "Content-Length: " + bytes.length + "\r\n\r\n" + json;
    }

    private static final class TestHttpResponse implements HttpResponse<String> {
        private final HttpRequest request;
        private final int statusCode;
        private final String body;

        private TestHttpResponse(HttpRequest request, int statusCode, String body) {
            this.request = request;
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
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
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (k, v) -> true);
        }

        @Override
        public String body() {
            return body;
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
