package io.openauth.sim.cli.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight JSON Schema validator for tests. Supports the subset we need:
 * type, required, properties, items, enum, additionalProperties (defaults to true), and examples (ignored for
 * validation).
 */
public final class JsonShapeAsserter {

    private JsonShapeAsserter() {}

    public static void assertMatchesShape(Path schemaPath, String actualJson) {
        try {
            Path resolved =
                    Files.exists(schemaPath) ? schemaPath : Path.of("..").resolve(schemaPath);
            Object schema = SimpleJson.parse(Files.readString(resolved));
            Object actual = SimpleJson.parse(actualJson);
            validate(resolved.toString(), schema, actual);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read schema file " + schemaPath, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validate(String source, Object schema, Object actual) {
        if (!(schema instanceof Map<?, ?> mapSchema)) {
            throw new IllegalArgumentException("Schema at " + source + " must be an object");
        }
        Map<String, Object> schemaMap = toSchemaMap(mapSchema);

        Object typeObj = schemaMap.get("type");
        if (typeObj != null) {
            if (typeObj instanceof List<?> types) {
                boolean matched =
                        types.stream().anyMatch(t -> matchesType(String.valueOf(t), actual, source, schemaMap));
                assertTrue(matched, () -> "Value does not match any allowed type " + types + " at " + source);
            } else {
                matchesType(String.valueOf(typeObj), actual, source, schemaMap);
            }
        } else {
            // no type: accept anything but still traverse if properties/items present
            if (schemaMap.containsKey("properties") || schemaMap.containsKey("items")) {
                validateObject(source, schemaMap, asMap(actual));
            }
        }

        if (schemaMap.containsKey("enum")) {
            List<Object> enums = (List<Object>) schemaMap.get("enum");
            assertTrue(enums.stream().anyMatch(e -> Objects.equals(e, actual)), () -> "Value not in enum at " + source);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObject(String source, Map<String, Object> schema, Map<String, Object> actual) {
        List<String> required = schema.containsKey("required") ? (List<String>) schema.get("required") : List.of();
        for (String field : required) {
            assertTrue(actual.containsKey(field), () -> "Missing required field '" + field + "' at " + source);
        }

        Map<String, Object> properties =
                schema.containsKey("properties") ? (Map<String, Object>) schema.get("properties") : Map.of();
        boolean additionalAllowed =
                !schema.containsKey("additionalProperties") || Boolean.TRUE.equals(schema.get("additionalProperties"));

        for (Map.Entry<String, Object> entry : actual.entrySet()) {
            String key = entry.getKey();
            if (properties.containsKey(key)) {
                validate(source + "." + key, properties.get(key), entry.getValue());
            } else {
                assertTrue(additionalAllowed, () -> "Unexpected field '" + key + "' at " + source);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateArray(String source, Map<String, Object> schema, List<Object> actual) {
        Object itemsSchema = schema.get("items");
        if (itemsSchema != null) {
            for (int i = 0; i < actual.size(); i++) {
                validate(source + "[" + i + "]", itemsSchema, actual.get(i));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) (Map<?, ?>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) (List<?>) value;
    }

    private static Map<String, Object> toSchemaMap(Map<?, ?> raw) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    private static boolean isInteger(Number number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            return true;
        }
        if (number instanceof BigDecimal dec) {
            return dec.stripTrailingZeros().scale() <= 0;
        }
        double value = number.doubleValue();
        return Math.rint(value) == value;
    }

    private static boolean matchesType(String type, Object actual, String source, Map<String, Object> schema) {
        switch (type) {
            case "object" -> {
                assertTrue(actual instanceof Map<?, ?>, () -> "Expected object at " + source);
                validateObject(source, schema, asMap(actual));
                return true;
            }
            case "array" -> {
                assertTrue(actual instanceof List<?>, () -> "Expected array at " + source);
                validateArray(source, schema, asList(actual));
                return true;
            }
            case "string" -> {
                assertTrue(actual instanceof String, () -> "Expected string at " + source);
                return true;
            }
            case "number" -> {
                assertTrue(actual instanceof Number, () -> "Expected number at " + source);
                return true;
            }
            case "integer" -> {
                assertTrue(
                        actual instanceof Number && isInteger((Number) actual), () -> "Expected integer at " + source);
                return true;
            }
            case "boolean" -> {
                assertTrue(actual instanceof Boolean, () -> "Expected boolean at " + source);
                return true;
            }
            case "null" -> {
                assertTrue(actual == null, () -> "Expected null at " + source);
                return true;
            }
            default -> throw new IllegalArgumentException("Unsupported type '" + type + "' in " + source);
        }
    }
}
