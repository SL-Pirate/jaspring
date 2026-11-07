package dev.isira.jaspring.typescript.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The JSON needed for the strings exchanged with the bundle, and nothing more: objects become `LinkedHashMap`,
/// arrays `List`, numbers `Long` or `Double`, plus `String`, `Boolean` and `null`. Written in-house because this
/// module depends on nothing but GraalJS.
public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        final var out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String text -> quote(text, out);
            case Boolean bool -> out.append(bool);
            case Number number -> out.append(number);
            case Map<?, ?> map -> {
                out.append('{');
                var first = true;
                for (final var entry : map.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    quote(String.valueOf(entry.getKey()), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            }
            case Iterable<?> list -> {
                out.append('[');
                var first = true;
                for (final var element : list) {
                    if (!first) out.append(',');
                    first = false;
                    write(element, out);
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("Cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    public static void quote(String text, StringBuilder out) {
        out.append('"');
        for (var i = 0; i < text.length(); i++) {
            final var c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    public static Object parse(String json) {
        final var parser = new Parser(json);
        final var value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != json.length()) throw parser.error("Trailing content");
        return value;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Object value() {
            skipWhitespace();
            if (pos >= text.length()) throw error("Unexpected end of JSON");
            return switch (text.charAt(pos)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            final var map = new LinkedHashMap<String, Object>();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw error("Expected a string key");
                final var key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                final var c = next();
                if (c == '}') return map;
                if (c != ',') throw error("Expected ',' or '}'");
            }
        }

        private List<Object> array() {
            final var list = new ArrayList<Object>();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                final var c = next();
                if (c == ']') return list;
                if (c != ',') throw error("Expected ',' or ']'");
            }
        }

        private String string() {
            pos++;
            final var out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) throw error("Unterminated string");
                final var c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) throw error("Unterminated escape");
                final var escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw error("Bad unicode escape");
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("Bad escape '\\" + escaped + "'");
                }
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) throw error("Unexpected token");
            pos += word.length();
            return value;
        }

        private Number number() {
            final var start = pos;
            if (peek() == '-') pos++;
            var integral = true;
            while (pos < text.length()) {
                final var c = text.charAt(pos);
                if (c >= '0' && c <= '9') pos++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    integral = false;
                    pos++;
                } else break;
            }
            final var token = text.substring(start, pos);
            if (token.isEmpty() || token.equals("-")) throw error("Unexpected token");
            try {
                // Not a conditional expression: Long and Double operands would both promote to double.
                if (integral) return Long.valueOf(token);
                return Double.valueOf(token);
            } catch (NumberFormatException e) {
                throw error("Bad number '" + token + "'");
            }
        }

        private char peek() {
            if (pos >= text.length()) throw error("Unexpected end of JSON");
            return text.charAt(pos);
        }

        private char next() {
            final var c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw error("Expected '" + c + "'");
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + pos);
        }
    }
}
