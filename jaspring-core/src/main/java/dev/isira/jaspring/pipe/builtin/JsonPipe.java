package dev.isira.jaspring.pipe.builtin;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.signals.Signal;
import dev.isira.jaspring.template.eval.Js;
import dev.isira.jaspring.template.expr.JsUndefined;

/// `JSON.stringify(value, null, 2)` for Java values: maps, collections, arrays, records and plain objects by their
/// public fields and record components. Debugging aid, as in Angular.
final class JsonPipe implements PipeTransform {

    @Override
    public Object transform(Object value, Object... args) {
        final var out = new StringBuilder();
        write(value, out, 0, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth, Set<Object> seen) {
        switch (value) {
            case null -> out.append("null");
            case JsUndefined u -> out.append("null");
            case Boolean b -> out.append(b);
            case Number n -> out.append(Js.toJsString(n));
            case CharSequence s -> string(s.toString(), out);
            case Character c -> string(c.toString(), out);
            case Enum<?> e -> string(e.name(), out);
            case Signal<?> s -> write(s.get(), out, depth, seen);
            case java.time.temporal.Temporal t -> string(t.toString(), out);
            case Map<?, ?> map -> object(map, out, depth, seen);
            case Collection<?> c -> array(c, out, depth, seen);
            default -> {
                if (value.getClass().isArray()) {
                    array(Js.arrayAsList(value), out, depth, seen);
                } else if (!seen.add(value)) {
                    out.append("\"[circular]\"");
                } else {
                    object(members(value), out, depth, seen);
                    seen.remove(value);
                }
            }
        }
    }

    private static Map<String, Object> members(Object value) {
        final var map = new java.util.LinkedHashMap<String, Object>();
        if (value.getClass().isRecord()) {
            for (final var component : value.getClass().getRecordComponents()) {
                try {
                    component.getAccessor().setAccessible(true);
                    map.put(component.getName(), component.getAccessor().invoke(value));
                } catch (ReflectiveOperationException e) {
                    map.put(component.getName(), null);
                }
            }
            return map;
        }
        for (final var field : value.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                map.put(field.getName(), field.get(value));
            } catch (IllegalAccessException e) {
                map.put(field.getName(), null);
            }
        }
        return map;
    }

    private static void object(Map<?, ?> map, StringBuilder out, int depth, Set<Object> seen) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        var first = true;
        for (final var entry : map.entrySet()) {
            if (!first) out.append(",\n");
            first = false;
            indent(out, depth + 1);
            string(Js.toJsString(entry.getKey()), out);
            out.append(": ");
            write(entry.getValue(), out, depth + 1, seen);
        }
        out.append('\n');
        indent(out, depth);
        out.append('}');
    }

    private static void array(Collection<?> items, StringBuilder out, int depth, Set<Object> seen) {
        if (items.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        var first = true;
        for (final var item : items) {
            if (!first) out.append(",\n");
            first = false;
            indent(out, depth + 1);
            write(item, out, depth + 1, seen);
        }
        out.append('\n');
        indent(out, depth);
        out.append(']');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static void string(String text, StringBuilder out) {
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
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
