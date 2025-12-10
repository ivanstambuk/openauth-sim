package io.openauth.sim.testing;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads the aggregated CLI schema and exposes Draft-07 definitions keyed by event name.
 * Lives in core test fixtures so application and CLI tests can share it without cross-module coupling.
 */
public final class CliSchemaLoader {

    private static final Map<String, Object> DEFINITIONS = loadDefinitions();

    private CliSchemaLoader() {}

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadDefinitions() {
        Path direct = Path.of("docs/3-reference/cli/cli.schema.json");
        Path path = Files.exists(direct) ? direct : Path.of("..", "docs/3-reference/cli/cli.schema.json");
        try {
            Object root = SimpleJson.parse(Files.readString(path));
            if (!(root instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Global CLI schema must be a JSON object: " + path);
            }
            Object definitions = map.get("definitions");
            if (!(definitions instanceof Map<?, ?> defs)) {
                throw new IllegalStateException("Global CLI schema is missing 'definitions': " + path);
            }
            return (Map<String, Object>) (Map<?, ?>) defs;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read global CLI schema at " + path, ex);
        }
    }

    public static Object schemaForEvent(String eventName) {
        Object schema = DEFINITIONS.get(eventName);
        if (schema == null) {
            throw new IllegalArgumentException("No CLI schema definition for event '" + eventName + "'");
        }
        return schema;
    }

    public static Map<String, Object> definitions() {
        return DEFINITIONS;
    }
}
