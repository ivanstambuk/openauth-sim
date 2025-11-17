package io.openauth.sim.tools.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class McpConfigLoaderTest {

    private final McpConfigLoader loader = new McpConfigLoader();

    @Test
    void returnsDefaultsWhenFileMissing() {
        McpConfig config = loader.load(Path.of("/tmp/does-not-exist-mcp-config.yaml"));
        assertEquals("http://localhost:8080", config.baseUrl().toString());
        assertEquals(Duration.ofSeconds(10), config.defaultTimeout());
        assertTrue(config.apiKey().isEmpty());
        assertTrue(config.toolTimeouts().isEmpty());
    }

    @Test
    void parsesCustomTimeoutsAndBaseUrl() throws IOException {
        Path tempFile = Files.createTempFile("mcp-config", ".yaml");
        Files.writeString(
                tempFile,
                "baseUrl: https://sim.local:8443\n" + "apiKey: test-key\n"
                        + "timeouts:\n"
                        + "  defaultMillis: 5000\n"
                        + "  hotp.evaluate: 7500\n");

        McpConfig config = loader.load(tempFile);
        assertEquals("https://sim.local:8443", config.baseUrl().toString());
        assertEquals(Duration.ofSeconds(5), config.defaultTimeout());
        assertEquals(Duration.ofMillis(7500), config.timeoutFor("hotp.evaluate"));
        assertEquals("test-key", config.apiKey().orElseThrow());
    }
}
