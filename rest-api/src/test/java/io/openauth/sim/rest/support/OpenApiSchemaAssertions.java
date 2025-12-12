package io.openauth.sim.rest.support;

import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.testing.JsonShapeAsserter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class OpenApiSchemaAssertions {

    private static final Map<String, Object> COMPONENT_SCHEMAS = loadComponentSchemas();

    private OpenApiSchemaAssertions() {}

    public static void assertMatchesComponentSchema(String schemaName, String actualJson) {
        Object schema = schema(schemaName);
        JsonShapeAsserter.assertMatchesShape(schema, actualJson, OpenApiSchemaAssertions::resolveRef);
    }

    public static Object schemaFor(String schemaName) {
        return schema(schemaName);
    }

    private static Object schema(String schemaName) {
        Object schema = COMPONENT_SCHEMAS.get(schemaName);
        if (schema == null) {
            throw new IllegalArgumentException("OpenAPI schema '" + schemaName + "' missing from snapshot");
        }
        return schema;
    }

    private static Object resolveRef(String ref) {
        String prefix = "#/components/schemas/";
        if (!ref.startsWith(prefix)) {
            throw new IllegalArgumentException("Unsupported $ref '" + ref + "'; expected " + prefix + "<SchemaName>");
        }
        String name = ref.substring(prefix.length());
        return schema(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadComponentSchemas() {
        Path snapshot = resolveSnapshotPath();
        try {
            Object parsed = SimpleJson.parse(Files.readString(snapshot));
            if (!(parsed instanceof Map<?, ?> root)) {
                throw new IllegalStateException("OpenAPI snapshot must be a JSON object: " + snapshot);
            }
            Object components = root.get("components");
            if (!(components instanceof Map<?, ?> componentsMap)) {
                throw new IllegalStateException("OpenAPI snapshot missing 'components': " + snapshot);
            }
            Object schemas = componentsMap.get("schemas");
            if (!(schemas instanceof Map<?, ?> schemaMap)) {
                throw new IllegalStateException("OpenAPI snapshot missing 'components.schemas': " + snapshot);
            }
            return (Map<String, Object>) (Map<?, ?>) schemaMap;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read OpenAPI snapshot at " + snapshot, ex);
        }
    }

    private static Path resolveSnapshotPath() {
        Path direct = Path.of("docs", "3-reference", "rest-openapi.json");
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("..", "docs", "3-reference", "rest-openapi.json");
    }
}
