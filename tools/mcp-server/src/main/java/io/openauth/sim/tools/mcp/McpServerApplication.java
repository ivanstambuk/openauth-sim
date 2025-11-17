package io.openauth.sim.tools.mcp;

import io.openauth.sim.tools.mcp.config.McpConfig;
import io.openauth.sim.tools.mcp.config.McpConfigLoader;
import io.openauth.sim.tools.mcp.http.JdkHttpExecutor;
import io.openauth.sim.tools.mcp.server.McpServer;
import io.openauth.sim.tools.mcp.tool.RestToolRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/** Entry point for the REST-backed MCP proxy. */
public final class McpServerApplication {

    private McpServerApplication() {}

    public static void main(String[] args) throws IOException {
        CliOptions options = CliOptions.parse(args);
        McpConfig config = new McpConfigLoader().load(options.configPath());

        if (options.listTools()) {
            RestToolRegistry.defaultRegistry()
                    .definitions()
                    .forEach(def -> System.out.println("- " + def.name() + " → " + def.description()));
            return;
        }

        RestToolRegistry registry = RestToolRegistry.defaultRegistry();
        try (InputStream in = System.in;
                OutputStream out = System.out) {
            McpServer server = new McpServer(config, registry, new JdkHttpExecutor(), in, out);
            server.run();
        }
    }

    private record CliOptions(Path configPath, boolean listTools) {

        static CliOptions parse(String[] args) {
            Path configPath = McpConfigLoader.defaultConfigPath();
            boolean listTools = false;
            for (int i = 0; i < args.length; i = i + 1) {
                String arg = args[i];
                if (arg.startsWith("--config=")) {
                    configPath = Path.of(arg.substring("--config=".length()));
                } else if ("--config".equals(arg) && i + 1 < args.length) {
                    i = i + 1;
                    configPath = Path.of(args[i]);
                } else if ("--list-tools".equals(arg)) {
                    listTools = true;
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    printHelpAndExit();
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new CliOptions(configPath, listTools);
        }

        private static void printHelpAndExit() {
            System.out.println("Usage: mcp-server [--config <path>] [--list-tools]");
            System.out.println("\nArguments:");
            System.out.println("  --config <path>    Override the default mcp-config.yaml location");
            System.out.println("  --list-tools       Print the available tool catalog and exit");
            System.exit(0);
        }
    }
}
