package io.openauth.sim.tools.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RestToolRegistryTest {

    @Test
    void exposesTotpHelperTool() {
        RestToolRegistry registry = RestToolRegistry.defaultRegistry();
        assertTrue(registry.findByName("totp.helper.currentOtp").isPresent());
    }
}
