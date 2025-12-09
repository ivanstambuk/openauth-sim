package io.openauth.sim.cli.support;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer for CLI payloads.
 *
 * <p>Supports Maps, Lists, Strings, Numbers, Booleans, enums, and null. Formatting avoids external
 * dependencies and keeps output deterministic for tests.
 */
public final class JsonPrinter {

    private JsonPrinter() {}

    public static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, null, null, false);
        return builder.toString();
    }

    public static String toPrettyJson(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, "", "  ", true);
        return builder.toString();
    }

    public static void print(PrintWriter writer, Object value, boolean pretty) {
        writer.println(pretty ? toPrettyJson(value) : toJson(value));
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(
            StringBuilder builder, Object value, String indent, String indentUnit, boolean pretty) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            writeString(builder, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(builder, (Map<Object, Object>) map, indent, indentUnit, pretty);
        } else if (value instanceof List<?> list) {
            writeArray(builder, list, indent, indentUnit, pretty);
        } else if (value.getClass().isEnum()) {
            writeString(builder, ((Enum<?>) value).name());
        } else {
            writeString(builder, value.toString());
        }
    }

    private static void writeObject(
            StringBuilder builder, Map<Object, Object> map, String indent, String indentUnit, boolean pretty) {
        String nextIndent = pretty && indent != null ? indent + indentUnit : null;
        builder.append('{');
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            if (!first) {
                builder.append(',');
            }
            first = false;
            if (pretty && nextIndent != null) {
                builder.append('\n').append(nextIndent);
            }
            writeString(builder, String.valueOf(entry.getKey()));
            builder.append(pretty ? ": " : ":");
            writeValue(builder, entry.getValue(), nextIndent, indentUnit, pretty);
        }
        if (pretty && !map.isEmpty() && indent != null) {
            builder.append('\n').append(indent);
        }
        builder.append('}');
    }

    private static void writeArray(
            StringBuilder builder, List<?> list, String indent, String indentUnit, boolean pretty) {
        String nextIndent = pretty && indent != null ? indent + indentUnit : null;
        builder.append('[');
        boolean first = true;
        for (Object element : list) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            if (pretty && nextIndent != null) {
                builder.append('\n').append(nextIndent);
            }
            writeValue(builder, element, nextIndent, indentUnit, pretty);
        }
        if (pretty && !list.isEmpty() && indent != null) {
            builder.append('\n').append(indent);
        }
        builder.append(']');
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        builder.append('"');
    }
}
