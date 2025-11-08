package io.openauth.sim.cli.eudi.openid4vp;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class JsonWriter {
    private JsonWriter() {}

    static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value);
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            writeString(builder, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(builder, map);
        } else if (value instanceof List<?> list) {
            writeArray(builder, list);
        } else if (value.getClass().isEnum()) {
            writeString(builder, ((Enum<?>) value).name());
        } else {
            writeString(builder, value.toString());
        }
    }

    private static void writeObject(StringBuilder builder, Map<?, ?> map) {
        builder.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            if (!first) {
                builder.append(',');
            }
            first = false;
            writeString(builder, String.valueOf(entry.getKey()));
            builder.append(':');
            writeValue(builder, entry.getValue());
        }
        builder.append('}');
    }

    private static void writeArray(StringBuilder builder, List<?> list) {
        builder.append('[');
        boolean first = true;
        for (Object element : list) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            writeValue(builder, element);
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
