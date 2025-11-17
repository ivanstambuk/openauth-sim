package io.openauth.sim.tools.mcp.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {}

    public static Object parse(String text) {
        return new Parser(text).parseValue();
    }

    public static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        write(value, builder);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> expectObject(Object value, String context) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, v) -> result.put(String.valueOf(key), v));
            return result;
        }
        throw new IllegalArgumentException("Expected object for " + context + " but found " + describe(value));
    }

    private static void write(Object value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String str) {
            writeString(str, builder);
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), builder);
                builder.append(':');
                write(entry.getValue(), builder);
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                write(item, builder);
            }
            builder.append(']');
        } else {
            writeString(value.toString(), builder);
        }
    }

    private static void writeString(String value, StringBuilder builder) {
        builder.append('"');
        for (int i = 0; i < value.length(); i = i + 1) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char ch = text.charAt(index);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return map;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    break;
                }
                expect(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    break;
                }
                expect(',');
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char ch = text.charAt(index);
                index = index + 1;
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    char next = text.charAt(index);
                    index = index + 1;
                    switch (next) {
                        case '\\' -> builder.append('\\');
                        case '"' -> builder.append('"');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            String hex = text.substring(index, index + 4);
                            builder.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape sequence: \\" + next);
                    }
                } else {
                    builder.append(ch);
                }
            }
            throw new IllegalArgumentException("Unterminated string literal");
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean token at position " + index);
        }

        private Object parseNull() {
            if (text.startsWith("null", index)) {
                index += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null token at position " + index);
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index = index + 1;
            }
            while (index < text.length()) {
                char ch = text.charAt(index);
                if (!Character.isDigit(ch)) {
                    break;
                }
                index = index + 1;
            }
            if (peek('.')) {
                index = index + 1;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index = index + 1;
                }
            }
            if (peek('e') || peek('E')) {
                index = index + 1;
                if (peek('+') || peek('-')) {
                    index = index + 1;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index = index + 1;
                }
            }
            String token = text.substring(start, index);
            return new BigDecimal(token);
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index = index + 1;
            }
        }

        private void expect(char expected) {
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
            }
            index = index + 1;
        }

        private boolean peek(char ch) {
            return index < text.length() && text.charAt(index) == ch;
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
