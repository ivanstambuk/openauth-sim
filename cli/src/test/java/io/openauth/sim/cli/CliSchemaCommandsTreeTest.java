package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guardrails to keep the OpenCLI-style commands tree aligned with the global CLI schema definitions.
 */
class CliSchemaCommandsTreeTest {

    private static final Set<String> REASON_CODE_ENUM_EXEMPT_DEFINITIONS =
            Set.of("cli.eudiw.request.create", "cli.eudiw.wallet.simulate", "cli.eudiw.validate");

    @Test
    void commandEventsAreDefinedAndUniquePerCommandPath() {
        Map<String, Object> schema = loadCliSchema();
        Map<String, Object> definitions = asMap(schema.get("definitions"));

        List<CommandEvent> commandEventBindings = extractCommandEvents(schema);
        Set<String> definitionEvents = new TreeSet<>(definitions.keySet());
        Set<String> commandEvents =
                commandEventBindings.stream().map(CommandEvent::event).collect(Collectors.toCollection(TreeSet::new));

        Set<String> missing = commandEvents.stream()
                .filter(event -> !definitionEvents.contains(event))
                .collect(Collectors.toCollection(TreeSet::new));
        assertTrue(missing.isEmpty(), () -> "Events missing definitions: " + missing);

        Map<String, Set<String>> pathsByEvent = new TreeMap<>();
        for (CommandEvent binding : commandEventBindings) {
            pathsByEvent
                    .computeIfAbsent(binding.event(), key -> new LinkedHashSet<>())
                    .add(formatPath(binding.commandPath()));
        }

        List<String> duplicates = pathsByEvent.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .toList();

        Set<CommandEvent> uniqueBindings = new LinkedHashSet<>(commandEventBindings);
        assertEquals(
                commandEvents.size(),
                uniqueBindings.size(),
                () -> "Events reused across multiple command paths: " + duplicates);
    }

    @Test
    void outputMetadataExamplesReferenceMatchingEvents() {
        Map<String, Object> schema = loadCliSchema();
        Map<String, Object> definitions = asMap(schema.get("definitions"));

        List<String> mismatches = new ArrayList<>();
        inspectCommandsForOutputExamples(asList(schema.get("commands")), List.of(), schema, mismatches);

        assertTrue(mismatches.isEmpty(), () -> "Output example event mismatches: " + mismatches);
    }

    @Test
    void definitionsUseDraft07WhenDeclared() {
        Map<String, Object> schema = loadCliSchema();
        Map<String, Object> definitions = asMap(schema.get("definitions"));

        List<String> unexpected = new ArrayList<>();
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            Map<String, Object> definition = asMap(entry.getValue());
            Object metaSchema = definition.get("$schema");
            if (metaSchema == null) {
                continue;
            }
            if (!"http://json-schema.org/draft-07/schema#".equals(metaSchema)) {
                unexpected.add(entry.getKey() + " -> " + metaSchema);
            }
        }

        assertTrue(unexpected.isEmpty(), () -> "Definitions with unexpected $schema: " + unexpected);
    }

    @Test
    void reasonCodeSchemasUseEnums() {
        Map<String, Object> schema = loadCliSchema();
        Map<String, Object> definitions = asMap(schema.get("definitions"));

        List<String> missingEnums = new ArrayList<>();
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            String definitionName = entry.getKey();
            if (REASON_CODE_ENUM_EXEMPT_DEFINITIONS.contains(definitionName)) {
                continue;
            }
            Map<String, Object> definition = asMap(entry.getValue());
            collectReasonCodeSchemasWithoutEnum(
                    definitionName, definition, "definitions." + definitionName, missingEnums);
        }

        assertTrue(missingEnums.isEmpty(), () -> "reasonCode fields without enum in CLI schema: " + missingEnums);
    }

    @Test
    void envelopeAndDataReasonCodeEnumsMatchWherePresent() {
        Map<String, Object> schema = loadCliSchema();
        Map<String, Object> definitions = asMap(schema.get("definitions"));

        List<String> mismatches = new ArrayList<>();

        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            String definitionName = entry.getKey();
            Map<String, Object> definition = asMap(entry.getValue());
            Object propertiesObj = definition.get("properties");
            if (!(propertiesObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> properties = asMap(propertiesObj);
            Object envelopeReasonObj = properties.get("reasonCode");
            Object dataObj = properties.get("data");
            if (!(envelopeReasonObj instanceof Map<?, ?>) || !(dataObj instanceof Map<?, ?>)) {
                continue;
            }

            Map<String, Object> dataSchema = asMap(dataObj);
            Object dataPropertiesObj = dataSchema.get("properties");
            if (!(dataPropertiesObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> dataProperties = asMap(dataPropertiesObj);
            Object dataReasonObj = dataProperties.get("reasonCode");
            if (!(dataReasonObj instanceof Map<?, ?>)) {
                continue;
            }

            Set<String> envelopeEnums = extractEnumValues(asMap(envelopeReasonObj));
            Set<String> dataEnums = extractEnumValues(asMap(dataReasonObj));
            if (envelopeEnums.isEmpty() || dataEnums.isEmpty()) {
                continue;
            }
            if (!envelopeEnums.equals(dataEnums)) {
                mismatches.add(definitionName + " -> envelope " + envelopeEnums + " vs data " + dataEnums);
            }
        }

        assertTrue(mismatches.isEmpty(), () -> "reasonCode enum mismatches between envelope and data: " + mismatches);
    }

    private static Map<String, Object> loadCliSchema() {
        Path direct = Path.of("docs/3-reference/cli/cli.schema.json");
        Path path = Files.exists(direct) ? direct : Path.of("..", "docs/3-reference/cli/cli.schema.json");
        try {
            Object parsed = SimpleJson.parse(Files.readString(path));
            return asMap(parsed);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read global CLI schema at " + path, ex);
        }
    }

    private static void collectReasonCodeSchemasWithoutEnum(
            String definitionName, Object node, String path, List<String> sink) {
        if (node instanceof Map<?, ?>) {
            Map<String, Object> map = asMap(node);

            Object propertiesObj = map.get("properties");
            if (propertiesObj instanceof Map<?, ?>) {
                Map<String, Object> properties = asMap(propertiesObj);
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String propertyName = entry.getKey();
                    Object propertySchema = entry.getValue();
                    String propertyPath = path + ".properties." + propertyName;

                    if ("reasonCode".equals(propertyName)) {
                        Map<String, Object> reasonSchema = asMap(propertySchema);
                        Object enumObj = reasonSchema.get("enum");
                        if (!(enumObj instanceof List<?> enums) || enums.isEmpty()) {
                            sink.add(definitionName + " -> " + propertyPath);
                        }
                    }

                    collectReasonCodeSchemasWithoutEnum(definitionName, propertySchema, propertyPath, sink);
                }
            }

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if ("properties".equals(entry.getKey())) {
                    continue;
                }
                Object child = entry.getValue();
                if (child instanceof Map<?, ?> || child instanceof List<?>) {
                    collectReasonCodeSchemasWithoutEnum(definitionName, child, path + "." + entry.getKey(), sink);
                }
            }
            return;
        }
        if (node instanceof List<?>) {
            List<?> list = (List<?>) node;
            for (int index = 0; index < list.size(); index++) {
                Object element = list.get(index);
                collectReasonCodeSchemasWithoutEnum(definitionName, element, path + "[" + index + "]", sink);
            }
        }
    }

    private static List<CommandEvent> extractCommandEvents(Map<String, Object> schema) {
        List<CommandEvent> events = new ArrayList<>();
        walkCommands(asList(schema.get("commands")), List.of(), events);
        return events;
    }

    @SuppressWarnings("unchecked")
    private static void walkCommands(List<Object> commands, List<String> path, List<CommandEvent> sink) {
        for (Object commandObj : commands) {
            Map<String, Object> command = asMap(commandObj);
            String name = Objects.toString(command.get("name"));
            List<String> newPath = new ArrayList<>(path);
            newPath.add(name);

            Object metadata = command.get("metadata");
            if (metadata instanceof List<?> metadataEntries) {
                for (Object entryObj : metadataEntries) {
                    Map<String, Object> entry = asMap(entryObj);
                    Object valueObj = entry.get("value");
                    if (valueObj instanceof Map<?, ?> value) {
                        Object event = value.get("event");
                        if (event != null) {
                            sink.add(new CommandEvent(List.copyOf(newPath), Objects.toString(event)));
                        }
                    }
                }
            }

            Object children = command.get("commands");
            if (children instanceof List<?> childList) {
                walkCommands((List<Object>) childList, newPath, sink);
            }
        }
    }

    private static void inspectCommandsForOutputExamples(
            List<Object> commands, List<String> path, Map<String, Object> schema, List<String> mismatches) {
        for (Object commandObj : commands) {
            Map<String, Object> command = asMap(commandObj);
            String name = Objects.toString(command.get("name"));
            List<String> newPath = new ArrayList<>(path);
            newPath.add(name);

            Object metadata = command.get("metadata");
            if (metadata instanceof List<?> metadataEntries) {
                for (Object entryObj : metadataEntries) {
                    Map<String, Object> entry = asMap(entryObj);
                    if (!"output".equals(entry.get("name"))) {
                        continue;
                    }
                    Map<String, Object> value = asMap(entry.get("value"));
                    String event = Objects.toString(value.get("event"));

                    Object examplesObj = value.get("examples");
                    if (!(examplesObj instanceof List<?> examples) || examples.isEmpty()) {
                        continue;
                    }
                    Object exampleObj = examples.get(0);
                    if (!(exampleObj instanceof Map<?, ?> example)) {
                        continue;
                    }
                    Object jsonRefObj = example.get("jsonRef");
                    if (!(jsonRefObj instanceof String jsonRef)) {
                        continue;
                    }

                    Object referenced = resolveJsonRef(schema, jsonRef);
                    if (!(referenced instanceof Map<?, ?> refMap)) {
                        mismatches.add(
                                "jsonRef " + jsonRef + " did not resolve to an object for " + formatPath(newPath));
                        continue;
                    }
                    Object referencedEvent = refMap.get("event");
                    if (!Objects.equals(event, referencedEvent)) {
                        mismatches.add(formatPath(newPath)
                                + " expected event "
                                + event
                                + " but jsonRef "
                                + jsonRef
                                + " has event "
                                + referencedEvent);
                    }
                }
            }

            Object children = command.get("commands");
            if (children instanceof List<?> childList) {
                inspectCommandsForOutputExamples(asList(children), newPath, schema, mismatches);
            }
        }
    }

    private static Object resolveJsonRef(Map<String, Object> root, String jsonRef) {
        if (!jsonRef.startsWith("#/")) {
            return null;
        }
        String[] parts = jsonRef.substring(2).split("/");
        Object current = root;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                int index = Integer.parseInt(part);
                current = list.get(index);
            } else {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        throw new IllegalArgumentException("Expected object but got: " + value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) (List<?>) list;
        }
        throw new IllegalArgumentException("Expected array but got: " + value);
    }

    private static String formatPath(List<String> path) {
        return String.join(" > ", path);
    }

    private static Set<String> extractEnumValues(Map<String, Object> schema) {
        Object enumObj = schema.get("enum");
        Set<String> values = new LinkedHashSet<>();
        if (enumObj instanceof List<?> list) {
            for (Object value : list) {
                values.add(String.valueOf(value));
            }
        }
        return values;
    }

    private record CommandEvent(List<String> commandPath, String event) {}
}
