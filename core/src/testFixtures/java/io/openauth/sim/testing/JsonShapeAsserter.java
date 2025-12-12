package io.openauth.sim.testing;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Lightweight JSON Schema validator for tests. Supports the subset we need:
 * type, required, properties, items, enum, additionalProperties (boolean or schema), and `$ref` (via resolver).
 */
public final class JsonShapeAsserter {

    @FunctionalInterface
    public interface RefResolver {
        Object resolve(String ref);
    }

    private JsonShapeAsserter() {}

    public static void assertMatchesShape(Path schemaPath, String actualJson) {
        assertMatchesShape(schemaPath, actualJson, null);
    }

    public static void assertMatchesShape(Path schemaPath, String actualJson, RefResolver resolver) {
        try {
            Path resolved =
                    Files.exists(schemaPath) ? schemaPath : Path.of("..").resolve(schemaPath);
            Object schema = SimpleJson.parse(Files.readString(resolved));
            Object actual = SimpleJson.parse(actualJson);
            validate(resolved.toString(), schema, actual, resolver, new LinkedHashSet<>());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read schema file " + schemaPath, ex);
        }
    }

    public static void assertMatchesShape(Object schema, String actualJson) {
        assertMatchesShape(schema, actualJson, null);
    }

    public static void assertMatchesShape(Object schema, String actualJson, RefResolver resolver) {
        Object actual = SimpleJson.parse(actualJson);
        validate("<inline-schema>", schema, actual, resolver, new LinkedHashSet<>());
    }

    @SuppressWarnings("unchecked")
    private static void validate(
            String source, Object schema, Object actual, RefResolver resolver, Set<String> seenRefs) {
        if (!(schema instanceof Map<?, ?> mapSchema)) {
            throw new IllegalArgumentException("Schema at " + source + " must be an object");
        }
        Map<String, Object> schemaMap = toSchemaMap(mapSchema);

        Object refObj = schemaMap.get("$ref");
        if (refObj != null) {
            if (resolver == null) {
                throw new IllegalArgumentException("Schema at " + source + " contains $ref but no resolver was set");
            }
            String ref = String.valueOf(refObj);
            if (!seenRefs.add(ref)) {
                throw new IllegalArgumentException("Detected recursive $ref '" + ref + "' while validating " + source);
            }
            try {
                Object resolved = resolver.resolve(ref);
                validate(source + "($ref=" + ref + ")", resolved, actual, resolver, seenRefs);
            } finally {
                seenRefs.remove(ref);
            }
            return;
        }

        validateAllOf(source, schemaMap, actual, resolver, seenRefs);
        validateAnyOf(source, schemaMap, actual, resolver, seenRefs);
        validateOneOf(source, schemaMap, actual, resolver, seenRefs);

        Object typeObj = schemaMap.get("type");
        if (typeObj != null) {
            if (typeObj instanceof List<?> types) {
                boolean matched = types.stream()
                        .anyMatch(t -> matchesType(String.valueOf(t), actual, source, schemaMap, resolver, seenRefs));
                require(matched, () -> "Value does not match any allowed type " + types + " at " + source);
            } else {
                matchesType(String.valueOf(typeObj), actual, source, schemaMap, resolver, seenRefs);
            }
        } else {
            // no type: accept anything but still traverse if properties/items present
            if (schemaMap.containsKey("properties") || schemaMap.containsKey("items")) {
                validateObject(source, schemaMap, asMap(actual), resolver, seenRefs);
            }
        }

        if (schemaMap.containsKey("enum")) {
            List<Object> enums = (List<Object>) schemaMap.get("enum");
            require(enums.stream().anyMatch(e -> Objects.equals(e, actual)), () -> "Value not in enum at " + source);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateAllOf(
            String source, Map<String, Object> schema, Object actual, RefResolver resolver, Set<String> seenRefs) {
        Object allOfObj = schema.get("allOf");
        if (allOfObj == null) {
            return;
        }
        if (!(allOfObj instanceof List<?> list)) {
            throw new IllegalArgumentException("'allOf' must be an array at " + source);
        }
        for (int index = 0; index < list.size(); index++) {
            validate(source + ".allOf[" + index + "]", list.get(index), actual, resolver, seenRefs);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateAnyOf(
            String source, Map<String, Object> schema, Object actual, RefResolver resolver, Set<String> seenRefs) {
        Object anyOfObj = schema.get("anyOf");
        if (anyOfObj == null) {
            return;
        }
        if (!(anyOfObj instanceof List<?> list)) {
            throw new IllegalArgumentException("'anyOf' must be an array at " + source);
        }
        List<String> mismatchMessages = new ArrayList<>();
        boolean matched = false;
        for (int index = 0; index < list.size(); index++) {
            try {
                validate(source + ".anyOf[" + index + "]", list.get(index), actual, resolver, seenRefs);
                matched = true;
                break;
            } catch (AssertionError ex) {
                mismatchMessages.add(ex.getMessage());
            }
        }
        require(
                matched,
                () -> "Value must match at least one anyOf variant at " + source + ", mismatches: " + mismatchMessages);
    }

    @SuppressWarnings("unchecked")
    private static void validateOneOf(
            String source, Map<String, Object> schema, Object actual, RefResolver resolver, Set<String> seenRefs) {
        Object oneOfObj = schema.get("oneOf");
        if (oneOfObj == null) {
            return;
        }
        if (!(oneOfObj instanceof List<?> list)) {
            throw new IllegalArgumentException("'oneOf' must be an array at " + source);
        }
        List<String> mismatchMessages = new ArrayList<>();
        int matches = 0;
        for (int index = 0; index < list.size(); index++) {
            try {
                validate(source + ".oneOf[" + index + "]", list.get(index), actual, resolver, seenRefs);
                matches++;
            } catch (AssertionError ex) {
                mismatchMessages.add(ex.getMessage());
            }
        }
        int matchCount = matches;
        List<String> mismatchSnapshot = List.copyOf(mismatchMessages);
        require(
                matches == 1,
                () -> "Value must match exactly one oneOf variant at "
                        + source
                        + " (matched "
                        + matchCount
                        + "), mismatches: "
                        + mismatchSnapshot);
    }

    @SuppressWarnings("unchecked")
    private static void validateObject(
            String source,
            Map<String, Object> schema,
            Map<String, Object> actual,
            RefResolver resolver,
            Set<String> seenRefs) {
        List<String> required = schema.containsKey("required") ? (List<String>) schema.get("required") : List.of();
        for (String field : required) {
            require(actual.containsKey(field), () -> "Missing required field '" + field + "' at " + source);
        }

        Map<String, Object> properties =
                schema.containsKey("properties") ? (Map<String, Object>) schema.get("properties") : Map.of();

        AdditionalProperties additional = resolveAdditionalProperties(schema);

        for (Map.Entry<String, Object> entry : actual.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (properties.containsKey(key)) {
                validate(source + "." + key, properties.get(key), value, resolver, seenRefs);
            } else if (additional.schema != null) {
                if (isTrivialAnySchema(additional.schema)) {
                    continue;
                }
                validate(source + "." + key, additional.schema, value, resolver, seenRefs);
            } else {
                require(additional.allowed, () -> "Unexpected field '" + key + "' at " + source);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateArray(
            String source,
            Map<String, Object> schema,
            List<Object> actual,
            RefResolver resolver,
            Set<String> seenRefs) {
        Object itemsSchema = schema.get("items");
        if (itemsSchema != null) {
            for (int i = 0; i < actual.size(); i++) {
                validate(source + "[" + i + "]", itemsSchema, actual.get(i), resolver, seenRefs);
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
        Map<String, Object> copy = new LinkedHashMap<>();
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

    private static boolean matchesType(
            String type,
            Object actual,
            String source,
            Map<String, Object> schema,
            RefResolver resolver,
            Set<String> seenRefs) {
        switch (type) {
            case "object" -> {
                require(actual instanceof Map<?, ?>, () -> "Expected object at " + source);
                validateObject(source, schema, asMap(actual), resolver, seenRefs);
                return true;
            }
            case "array" -> {
                require(actual instanceof List<?>, () -> "Expected array at " + source);
                validateArray(source, schema, asList(actual), resolver, seenRefs);
                return true;
            }
            case "string" -> {
                require(actual instanceof String, () -> "Expected string at " + source);
                return true;
            }
            case "number" -> {
                require(actual instanceof Number, () -> "Expected number at " + source);
                return true;
            }
            case "integer" -> {
                require(actual instanceof Number && isInteger((Number) actual), () -> "Expected integer at " + source);
                return true;
            }
            case "boolean" -> {
                require(actual instanceof Boolean, () -> "Expected boolean at " + source);
                return true;
            }
            case "null" -> {
                require(actual == null, () -> "Expected null at " + source);
                return true;
            }
            default -> throw new IllegalArgumentException("Unsupported type '" + type + "' in " + source);
        }
    }

    private static AdditionalProperties resolveAdditionalProperties(Map<String, Object> schema) {
        if (!schema.containsKey("additionalProperties")) {
            return AdditionalProperties.permissive();
        }
        Object additional = schema.get("additionalProperties");
        if (additional instanceof Boolean allowed) {
            return allowed ? AdditionalProperties.permissive() : AdditionalProperties.restrictive();
        }
        if (additional instanceof Map<?, ?> mapSchema) {
            return AdditionalProperties.valueSchema(mapSchema);
        }
        throw new IllegalArgumentException("Unsupported additionalProperties value: " + additional);
    }

    private static boolean isTrivialAnySchema(Object schema) {
        if (!(schema instanceof Map<?, ?> mapSchema)) {
            return false;
        }
        Map<String, Object> schemaMap = toSchemaMap(mapSchema);
        Object type = schemaMap.get("type");
        if (type == null) {
            return schemaMap.get("$ref") == null
                    && !schemaMap.containsKey("properties")
                    && !schemaMap.containsKey("items")
                    && !schemaMap.containsKey("enum")
                    && !schemaMap.containsKey("required");
        }
        if ("object".equals(String.valueOf(type))) {
            return !schemaMap.containsKey("$ref")
                    && !schemaMap.containsKey("properties")
                    && !schemaMap.containsKey("items")
                    && !schemaMap.containsKey("enum")
                    && !schemaMap.containsKey("required");
        }
        return false;
    }

    private record AdditionalProperties(boolean allowed, Object schema) {
        static AdditionalProperties permissive() {
            return new AdditionalProperties(true, null);
        }

        static AdditionalProperties restrictive() {
            return new AdditionalProperties(false, null);
        }

        static AdditionalProperties valueSchema(Map<?, ?> schema) {
            return new AdditionalProperties(true, schema);
        }
    }

    private static void require(boolean condition, Supplier<String> message) {
        if (!condition) {
            throw new AssertionError(message.get());
        }
    }
}
