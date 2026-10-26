package dev.isira.jaspring.template.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.isira.jaspring.template.expr.JsUndefined;

/// The JavaScript members Angular templates use on strings, arrays and numbers (`items.length`, `name.toUpperCase()`,
/// `list.filter(x => ...)`), mapped onto Java values so templates written for Angular keep working.
final class JsShim {

    static final Object NONE = new Object();

    private JsShim() {}

    static Object read(Object target, String name) {
        return switch (target) {
            case CharSequence s -> string(s.toString(), name);
            case List<?> list -> list(list, name);
            case Number n -> number(n, name);
            default -> target.getClass().isArray() ? list(arrayAsList(target), name) : NONE;
        };
    }

    static List<Object> arrayAsList(Object array) {
        return Js.arrayAsList(array);
    }

    private static Object string(String s, String name) {
        return switch (name) {
            case "length" -> (long) s.length();
            case "toUpperCase" -> fn(a -> s.toUpperCase());
            case "toLowerCase" -> fn(a -> s.toLowerCase());
            case "trim" -> fn(a -> s.trim());
            case "trimStart" -> fn(a -> s.stripLeading());
            case "trimEnd" -> fn(a -> s.stripTrailing());
            case "startsWith" -> fn(a -> s.startsWith(Js.toJsString(a[0])));
            case "endsWith" -> fn(a -> s.endsWith(Js.toJsString(a[0])));
            case "includes" -> fn(a -> s.contains(Js.toJsString(a[0])));
            case "indexOf" -> fn(a -> (long) s.indexOf(Js.toJsString(a[0])));
            case "lastIndexOf" -> fn(a -> (long) s.lastIndexOf(Js.toJsString(a[0])));
            case "charAt" -> fn(a -> index(a, 0, 0) < s.length() ? String.valueOf(s.charAt(index(a, 0, 0))) : "");
            case "at" -> fn(a -> {
                final var i = at(index(a, 0, 0), s.length());
                return i < 0 ? JsUndefined.INSTANCE : String.valueOf(s.charAt(i));
            });
            case "slice" -> fn(a -> s.substring(sliceStart(a, 0, s.length()), sliceEnd(a, 1, s.length())));
            case "substring" -> fn(a -> {
                final var start = Math.clamp(index(a, 0, 0), 0, s.length());
                final var end = Math.clamp(index(a, 1, s.length()), 0, s.length());
                return s.substring(Math.min(start, end), Math.max(start, end));
            });
            case "split" -> fn(a -> a.length == 0 || Js.isNullish(a[0]) ? List.of(s) : split(s, Js.toJsString(a[0])));
            case "replace" -> fn(a -> s.replaceFirst(java.util.regex.Pattern.quote(Js.toJsString(a[0])), java.util.regex.Matcher.quoteReplacement(Js.toJsString(a[1]))));
            case "replaceAll" -> fn(a -> s.replace(Js.toJsString(a[0]), Js.toJsString(a[1])));
            case "repeat" -> fn(a -> s.repeat(index(a, 0, 0)));
            case "padStart" -> fn(a -> pad(s, index(a, 0, 0), a.length > 1 ? Js.toJsString(a[1]) : " ", true));
            case "padEnd" -> fn(a -> pad(s, index(a, 0, 0), a.length > 1 ? Js.toJsString(a[1]) : " ", false));
            case "concat" -> fn(a -> s + Arrays.stream(a).map(Js::toJsString).reduce("", String::concat));
            case "toString" -> fn(a -> s);
            default -> NONE;
        };
    }

    private static Object list(List<?> list, String name) {
        return switch (name) {
            case "length" -> (long) list.size();
            case "includes" -> fn(a -> list.stream().anyMatch(item -> Js.strictEquals(item, a[0])));
            case "indexOf" -> fn(a -> {
                for (var i = 0; i < list.size(); i++) {
                    if (Js.strictEquals(list.get(i), a[0])) return (long) i;
                }
                return -1L;
            });
            case "at" -> fn(a -> {
                final var i = at(index(a, 0, 0), list.size());
                return i < 0 ? JsUndefined.INSTANCE : list.get(i);
            });
            case "slice" -> fn(a -> new ArrayList<>(list.subList(sliceStart(a, 0, list.size()), sliceEnd(a, 1, list.size()))));
            case "join" -> fn(a -> {
                final var separator = a.length == 0 || Js.isNullish(a[0]) ? "," : Js.toJsString(a[0]);
                return list.stream().map(item -> Js.isNullish(item) ? "" : Js.toJsString(item)).reduce((x, y) -> x + separator + y).orElse("");
            });
            case "concat" -> fn(a -> {
                final var result = new ArrayList<Object>(list);
                for (final var arg : a) {
                    if (arg instanceof List<?> other) result.addAll(other); else result.add(arg);
                }
                return result;
            });
            case "map" -> fn(a -> {
                final var fn = callback(a);
                final var result = new ArrayList<Object>(list.size());
                for (var i = 0; i < list.size(); i++) {
                    result.add(fn.call(list.get(i), (long) i, list));
                }
                return result;
            });
            case "filter" -> fn(a -> {
                final var fn = callback(a);
                final var result = new ArrayList<Object>();
                for (var i = 0; i < list.size(); i++) {
                    if (Js.truthy(fn.call(list.get(i), (long) i, list))) result.add(list.get(i));
                }
                return result;
            });
            case "find" -> fn(a -> {
                final var fn = callback(a);
                for (var i = 0; i < list.size(); i++) {
                    if (Js.truthy(fn.call(list.get(i), (long) i, list))) return list.get(i);
                }
                return JsUndefined.INSTANCE;
            });
            case "findIndex" -> fn(a -> {
                final var fn = callback(a);
                for (var i = 0; i < list.size(); i++) {
                    if (Js.truthy(fn.call(list.get(i), (long) i, list))) return (long) i;
                }
                return -1L;
            });
            case "some" -> fn(a -> {
                final var fn = callback(a);
                for (var i = 0; i < list.size(); i++) {
                    if (Js.truthy(fn.call(list.get(i), (long) i, list))) return true;
                }
                return false;
            });
            case "every" -> fn(a -> {
                final var fn = callback(a);
                for (var i = 0; i < list.size(); i++) {
                    if (!Js.truthy(fn.call(list.get(i), (long) i, list))) return false;
                }
                return true;
            });
            case "reduce" -> fn(a -> {
                final var fn = callback(a);
                var start = 0;
                Object acc;
                if (a.length > 1) {
                    acc = a[1];
                } else {
                    if (list.isEmpty()) throw new EvaluationException("Reduce of empty array with no initial value");
                    acc = list.get(0);
                    start = 1;
                }
                for (var i = start; i < list.size(); i++) {
                    acc = fn.call(acc, list.get(i), (long) i, list);
                }
                return acc;
            });
            case "forEach" -> fn(a -> {
                final var fn = callback(a);
                for (var i = 0; i < list.size(); i++) {
                    fn.call(list.get(i), (long) i, list);
                }
                return JsUndefined.INSTANCE;
            });
            case "flat" -> fn(a -> {
                final var result = new ArrayList<Object>();
                for (final var item : list) {
                    if (item instanceof List<?> inner) result.addAll(inner); else result.add(item);
                }
                return result;
            });
            case "toString" -> fn(a -> Js.toJsString(list));
            default -> NONE;
        };
    }

    private static Object number(Number n, String name) {
        return switch (name) {
            case "toFixed" -> fn(a -> String.format(java.util.Locale.ROOT, "%." + index(a, 0, 0) + "f", n.doubleValue()));
            case "toString" -> fn(a -> Js.toJsString(n));
            default -> NONE;
        };
    }

    private static JsFunction callback(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof JsFunction fn)) {
            throw new EvaluationException("Callback is not a function");
        }
        return fn;
    }

    private static JsFunction fn(JsFunction body) {
        return body;
    }

    private static int index(Object[] args, int position, int fallback) {
        if (args.length <= position || Js.isNullish(args[position])) return fallback;
        return (int) Js.toDouble(args[position]);
    }

    private static int at(int index, int size) {
        final var i = index < 0 ? size + index : index;
        return i >= 0 && i < size ? i : -1;
    }

    private static int sliceStart(Object[] args, int position, int size) {
        final var start = index(args, position, 0);
        return Math.clamp(start < 0 ? size + start : start, 0, size);
    }

    private static int sliceEnd(Object[] args, int position, int size) {
        final var end = index(args, position, size);
        return Math.clamp(end < 0 ? size + end : end, sliceStart(args, position - 1, size), size);
    }

    private static List<Object> split(String s, String separator) {
        if (separator.isEmpty()) {
            final var chars = new ArrayList<Object>(s.length());
            s.chars().forEach(c -> chars.add(String.valueOf((char) c)));
            return chars;
        }
        return new ArrayList<>(Arrays.asList((Object[]) s.split(java.util.regex.Pattern.quote(separator), -1)));
    }

    private static String pad(String s, int length, String filler, boolean start) {
        if (s.length() >= length || filler.isEmpty()) return s;
        final var padding = new StringBuilder();
        while (padding.length() < length - s.length()) {
            padding.append(filler);
        }
        padding.setLength(length - s.length());
        return start ? padding + s : s + padding;
    }

    static boolean isObjectLike(Object value) {
        return value instanceof Map<?, ?> || !(Objects.isNull(value) || value instanceof CharSequence || value instanceof Number || value instanceof Boolean);
    }
}
