package io.openauth.sim.tools.mcp.tool;

import io.openauth.sim.tools.mcp.config.McpConfig;
import java.util.Map;

public interface RestToolDefinition {
    String name();

    String description();

    Map<String, Object> descriptor();

    RestToolInvocation createInvocation(McpConfig config, Map<String, Object> arguments);
}
