package io.openauth.sim.cli.support;

import io.openauth.sim.testing.CliSchemaLoader;
import java.util.Map;

/**
 * Helper for loading per-event CLI JSON Schemas from the global CLI schema file. The file is an OpenCLI descriptor,
 * so this loader intentionally skips the top-level `$schema` and OpenCLI keywords and treats `definitions` as the
 * canonical registry for Draft-07 fragments.
 */
public final class CliJsonSchemas {
    private static final Map<String, Object> DEFINITIONS = CliSchemaLoader.definitions();

    private CliJsonSchemas() {}

    public static Object schemaForEvent(String eventName) {
        Object schema = DEFINITIONS.get(eventName);
        if (schema == null) {
            throw new IllegalArgumentException("No CLI schema definition for event '" + eventName + "'");
        }
        return schema;
    }
}
