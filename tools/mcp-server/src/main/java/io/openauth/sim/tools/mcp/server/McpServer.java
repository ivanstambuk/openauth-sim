package io.openauth.sim.tools.mcp.server;

import io.openauth.sim.tools.mcp.config.McpConfig;
import io.openauth.sim.tools.mcp.http.HttpExecutor;
import io.openauth.sim.tools.mcp.json.Json;
import io.openauth.sim.tools.mcp.tool.RestToolDefinition;
import io.openauth.sim.tools.mcp.tool.RestToolInvocation;
import io.openauth.sim.tools.mcp.tool.RestToolRegistry;
import io.openauth.sim.tools.mcp.transport.ContentLengthMessageFramer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class McpServer {
    private final McpConfig config;
    private final RestToolRegistry registry;
    private final HttpExecutor executor;
    private final ContentLengthMessageFramer framer;
    private volatile boolean shutdown;

    public McpServer(
            McpConfig config, RestToolRegistry registry, HttpExecutor executor, InputStream in, OutputStream out) {
        this.config = config;
        this.registry = registry;
        this.executor = executor;
        this.framer = new ContentLengthMessageFramer(in, out);
    }

    public void run() throws IOException {
        while (!shutdown) {
            Optional<String> message = framer.read();
            if (message.isEmpty()) {
                break;
            }
            handleMessage(message.get());
        }
    }

    private void handleMessage(String payload) throws IOException {
        Object parsed = Json.parse(payload);
        Map<String, Object> message = Json.expectObject(parsed, "message");
        Object id = message.get("id");
        String method = (String) message.get("method");
        if (method == null) {
            sendError(id, -32600, "Invalid request", "Missing method field");
            return;
        }
        try {
            switch (method) {
                case "initialize" -> handleInitialize(id);
                case "ping" -> sendResponse(id, Map.of("status", "ok"));
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, message);
                case "shutdown" -> handleShutdown(id);
                default -> sendError(id, -32601, "Unknown method", method);
            }
        } catch (IllegalArgumentException ex) {
            sendError(id, -32602, "Invalid params", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sendError(id, -32000, "Invocation interrupted", ex.getMessage());
        } catch (IOException ex) {
            sendError(id, -32001, "Invocation failed", ex.getMessage());
        }
    }

    private void handleInitialize(Object id) throws IOException {
        Map<String, Object> capabilities = Map.of("tools", Map.of("listChanged", false));
        sendResponse(id, Map.of("capabilities", capabilities));
        sendNotification(
                "notifications/initialized",
                Map.of("server", Map.of("name", "OpenAuth Simulator MCP Proxy", "version", "0.1")));
    }

    private void handleToolsList(Object id) throws IOException {
        List<Map<String, Object>> tools = registry.definitions().stream()
                .map(RestToolDefinition::descriptor)
                .toList();
        Map<String, Object> telemetry = buildCatalogTelemetry(tools);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        result.put("telemetry", telemetry);
        sendResponse(id, result);
    }

    private void handleToolsCall(Object id, Map<String, Object> message) throws IOException, InterruptedException {
        Map<String, Object> params = Json.expectObject(message.get("params"), "tools/call params");
        String toolName = (String) params.get("name");
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        Map<String, Object> arguments = params.containsKey("arguments")
                ? Json.expectObject(params.get("arguments"), "tools/call arguments")
                : Map.of();
        RestToolDefinition definition = registry.findByName(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
        RestToolInvocation invocation = definition.createInvocation(config, arguments);
        HttpResponse<String> response = executor.execute(invocation.request());
        sendResponse(id, buildToolResult(invocation, response));
    }

    private Map<String, Object> buildToolResult(RestToolInvocation invocation, HttpResponse<String> response) {
        String toolName = invocation.toolName();
        String body = response.body() == null ? "" : response.body();
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP ").append(response.statusCode()).append('\n');
        if (!body.isBlank()) {
            builder.append(body);
        }
        Map<String, Object> content = Map.of("type", "text", "text", builder.toString());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", toolName);
        result.put("statusCode", response.statusCode());
        result.put("content", List.of(content));
        result.put("isError", response.statusCode() >= 400);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> telemetry =
                    switch (toolName) {
                        case "totp.helper.currentOtp" -> buildTotpHelperTelemetry(body);
                        case "fixtures.list" -> buildFixturesListTelemetry(invocation, body);
                        default -> Map.of();
                    };
            if (!telemetry.isEmpty()) {
                result.put("telemetry", telemetry);
            }
        }
        return result;
    }

    private Map<String, Object> buildCatalogTelemetry(List<Map<String, Object>> tools) {
        String telemetryId = "mcp.catalog." + UUID.randomUUID();
        int toolCount = tools.size();
        String schemaHash = computeSchemaHash(tools);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "mcp.catalog.listed");
        payload.put("telemetryId", telemetryId);
        payload.put("toolCount", toolCount);
        payload.put("schemaHash", schemaHash);
        payload.put("sanitized", Boolean.TRUE);
        return payload;
    }

    private Map<String, Object> buildTotpHelperTelemetry(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = Json.parse(responseBody);
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
        Map<String, Object> root;
        try {
            root = Json.expectObject(parsed, "totp.helper.currentOtp response");
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
        Object credentialIdValue = root.get("credentialId");
        Object generationValue = root.get("generationEpochSeconds");
        if (credentialIdValue == null || !(generationValue instanceof Number)) {
            return Map.of();
        }
        String credentialId = credentialIdValue.toString();
        long generationEpochSeconds = ((Number) generationValue).longValue();
        String telemetryId = extractTelemetryId(root);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "mcp.totp.helper.lookup");
        payload.put("telemetryId", telemetryId);
        payload.put("credentialId", sha256Hex(credentialId));
        payload.put("generationEpochSeconds", generationEpochSeconds);
        payload.put("sanitized", Boolean.TRUE);
        return payload;
    }

    private String extractTelemetryId(Map<String, Object> root) {
        Object metadata = root.get("metadata");
        if (metadata instanceof Map<?, ?> rawMetadata) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMetadata.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            Object telemetryIdValue = normalized.get("telemetryId");
            if (telemetryIdValue != null) {
                String telemetryId = telemetryIdValue.toString().trim();
                if (!telemetryId.isEmpty()) {
                    return telemetryId;
                }
            }
        }
        return "mcp.totp.helper-" + UUID.randomUUID();
    }

    private Map<String, Object> buildFixturesListTelemetry(RestToolInvocation invocation, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = Json.parse(responseBody);
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
        if (!(parsed instanceof List<?> list)) {
            return Map.of();
        }
        List<String> presetIds = new java.util.ArrayList<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> raw) {
                Object id = raw.get("id");
                if (id != null) {
                    String value = id.toString().trim();
                    if (!value.isEmpty()) {
                        presetIds.add(value);
                    }
                }
            }
        }
        String protocol = inferProtocol(invocation);
        String telemetryId = "mcp.fixtures.list-" + UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "mcp.fixtures.list");
        payload.put("telemetryId", telemetryId);
        payload.put("protocol", protocol);
        payload.put("presetCount", presetIds.size());
        payload.put("presetIds", List.copyOf(presetIds));
        payload.put("sanitized", Boolean.TRUE);
        return payload;
    }

    private String inferProtocol(RestToolInvocation invocation) {
        String path = invocation.request().uri().getPath();
        if (path == null || path.isBlank()) {
            return "unknown";
        }
        if (path.contains("/hotp/credentials")) {
            return "hotp";
        }
        if (path.contains("/totp/credentials")) {
            return "totp";
        }
        if (path.contains("/ocra/credentials")) {
            return "ocra";
        }
        if (path.contains("/emv/cap/credentials")) {
            return "emv";
        }
        if (path.contains("/webauthn/credentials")) {
            return "fido2";
        }
        return "unknown";
    }

    private String computeSchemaHash(List<Map<String, Object>> tools) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tools", tools);
        String json = Json.stringify(payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", ex);
        }
    }

    private String sha256Hex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return "sha256:" + builder;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", ex);
        }
    }

    private void handleShutdown(Object id) throws IOException {
        shutdown = true;
        sendResponse(id, Map.of("status", "shutting_down"));
    }

    private void sendResponse(Object id, Map<String, Object> result) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        framer.write(Json.stringify(response));
    }

    private void sendError(Object id, int code, String message, String details) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null && !details.isBlank()) {
            error.put("data", Map.of("details", details));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("error", error);
        framer.write(Json.stringify(payload));
    }

    private void sendNotification(String method, Map<String, Object> params) throws IOException {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);
        framer.write(Json.stringify(notification));
    }
}
