package io.openauth.sim.tools.mcp.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class JsonTest {

    @Test
    void parseAndStringifyRoundTrip() {
        String json = "{\"name\":\"test\",\"count\":2,\"items\":[true,false]}";
        Object parsed = Json.parse(json);
        assertTrue(parsed instanceof Map);
        String rewritten = Json.stringify(parsed);
        assertEquals(json, rewritten);
    }
}
