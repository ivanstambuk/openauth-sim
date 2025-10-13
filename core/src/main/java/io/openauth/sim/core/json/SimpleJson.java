package io.openauth.sim.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser used by fixture loaders. */
public final class SimpleJson {

  private SimpleJson() {
    throw new AssertionError("Utility class");
  }

  /** Parse a JSON document into standard {@link java.util} types. */
  public static Object parse(String input) {
    return new Parser(input).parse();
  }

  private static final class Parser {
    private final String input;
    private int index;

    private Parser(String input) {
      this.input = input;
    }

    Object parse() {
      skipWhitespace();
      Object value = readValue();
      skipWhitespace();
      if (!isAtEnd()) {
        throw new IllegalStateException("Unexpected trailing data in JSON bundle");
      }
      return value;
    }

    private Object readValue() {
      skipWhitespace();
      if (isAtEnd()) {
        throw new IllegalStateException("Unexpected end of JSON input");
      }
      char ch = input.charAt(index);
      return switch (ch) {
        case '{' -> readObject();
        case '[' -> readArray();
        case '"' -> readString();
        case 't', 'f' -> readBoolean();
        case 'n' -> readNull();
        default -> readNumber();
      };
    }

    private Map<String, Object> readObject() {
      expect('{');
      Map<String, Object> map = new LinkedHashMap<>();
      skipWhitespace();
      if (peek('}')) {
        index++;
        return map;
      }
      while (true) {
        skipWhitespace();
        String key = readString();
        skipWhitespace();
        expect(':');
        skipWhitespace();
        Object value = readValue();
        map.put(key, value);
        skipWhitespace();
        if (peek('}')) {
          index++;
          break;
        }
        expect(',');
      }
      return map;
    }

    private List<Object> readArray() {
      expect('[');
      List<Object> values = new ArrayList<>();
      skipWhitespace();
      if (peek(']')) {
        index++;
        return values;
      }
      while (true) {
        values.add(readValue());
        skipWhitespace();
        if (peek(']')) {
          index++;
          break;
        }
        expect(',');
      }
      return values;
    }

    private String readString() {
      expect('"');
      StringBuilder builder = new StringBuilder();
      while (!isAtEnd()) {
        char ch = input.charAt(index++);
        if (ch == '"') {
          return builder.toString();
        }
        if (ch == '\\') {
          if (isAtEnd()) {
            throw new IllegalStateException("Unterminated escape sequence in JSON string");
          }
          char escape = input.charAt(index++);
          builder.append(
              switch (escape) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> readUnicode();
                default -> throw new IllegalStateException("Invalid escape character: " + escape);
              });
        } else {
          builder.append(ch);
        }
      }
      throw new IllegalStateException("Unterminated JSON string literal");
    }

    private char readUnicode() {
      if (index + 4 > input.length()) {
        throw new IllegalStateException("Incomplete unicode escape sequence");
      }
      int codePoint = 0;
      for (int i = 0; i < 4; i++) {
        char hex = input.charAt(index++);
        codePoint <<= 4;
        if (hex >= '0' && hex <= '9') {
          codePoint |= hex - '0';
        } else if (hex >= 'a' && hex <= 'f') {
          codePoint |= hex - 'a' + 10;
        } else if (hex >= 'A' && hex <= 'F') {
          codePoint |= hex - 'A' + 10;
        } else {
          throw new IllegalStateException("Invalid hex digit in unicode escape: " + hex);
        }
      }
      return (char) codePoint;
    }

    private Boolean readBoolean() {
      if (matches("true")) {
        index += 4;
        return Boolean.TRUE;
      }
      if (matches("false")) {
        index += 5;
        return Boolean.FALSE;
      }
      throw new IllegalStateException("Invalid boolean literal in JSON input");
    }

    private Object readNull() {
      if (!matches("null")) {
        throw new IllegalStateException("Invalid null literal in JSON input");
      }
      index += 4;
      return null;
    }

    private Number readNumber() {
      int start = index;
      if (peek('-')) {
        index++;
      }
      readDigits();
      if (peek('.')) {
        index++;
        readDigits();
      }
      if (peek('e') || peek('E')) {
        index++;
        if (peek('+') || peek('-')) {
          index++;
        }
        readDigits();
      }
      String literal = input.substring(start, index);
      try {
        if (literal.contains(".") || literal.contains("e") || literal.contains("E")) {
          return Double.parseDouble(literal);
        }
        long value = Long.parseLong(literal);
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
          return (int) value;
        }
        return value;
      } catch (NumberFormatException ex) {
        throw new IllegalStateException("Invalid numeric literal in JSON input", ex);
      }
    }

    private void readDigits() {
      if (isAtEnd() || !Character.isDigit(input.charAt(index))) {
        throw new IllegalStateException("Expected digit in JSON number");
      }
      while (!isAtEnd() && Character.isDigit(input.charAt(index))) {
        index++;
      }
    }

    private void expect(char expected) {
      if (isAtEnd() || input.charAt(index) != expected) {
        throw new IllegalStateException("Expected '" + expected + "' in JSON input");
      }
      index++;
    }

    private boolean peek(char expected) {
      return !isAtEnd() && input.charAt(index) == expected;
    }

    private boolean matches(String text) {
      return input.startsWith(text, index);
    }

    private void skipWhitespace() {
      while (!isAtEnd()) {
        char ch = input.charAt(index);
        if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
          index++;
        } else {
          break;
        }
      }
    }

    private boolean isAtEnd() {
      return index >= input.length();
    }
  }
}
