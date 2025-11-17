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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        sendResponse(id, Map.of("tools", tools));
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
        sendResponse(id, buildToolResult(toolName, response));
    }

    private Map<String, Object> buildToolResult(String toolName, HttpResponse<String> response) {
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
        return result;
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
