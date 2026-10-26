package dev.isira.jaspring.template.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

import dev.isira.jaspring.signals.Signal;
import dev.isira.jaspring.template.expr.JsUndefined;

/// JavaScript value semantics over Java objects: truthiness, equality, coercion and stringification.
/// Integral numbers are `Long`, everything else `Double`; results are normalized so `3.0` becomes `3L` like JS.
public final class Js {

    private static final long MAX_SAFE_INTEGER = 1L << 53;

    private Js() {}

    /// Copies any Java array (primitive or not) into a list, which is how templates see arrays.
    public static java.util.List<Object> arrayAsList(Object array) {
        final var length = java.lang.reflect.Array.getLength(array);
        final var list = new java.util.ArrayList<Object>(length);
        for (var i = 0; i < length; i++) {
            list.add(java.lang.reflect.Array.get(array, i));
        }
        return list;
    }

    public static boolean isNullish(Object value) {
        return value == null || value == JsUndefined.INSTANCE;
    }

    public static boolean truthy(Object value) {
        return switch (value) {
            case null -> false;
            case JsUndefined u -> false;
            case Boolean b -> b;
            case Number n -> {
                final var d = n.doubleValue();
                yield d != 0 && !Double.isNaN(d);
            }
            case CharSequence s -> !s.isEmpty();
            default -> true;
        };
    }

    public static boolean strictEquals(Object a, Object b) {
        if (a == null || b == null || a == JsUndefined.INSTANCE || b == JsUndefined.INSTANCE) {
            return a == b;
        }
        if (a instanceof Number x && b instanceof Number y) return numbersEqual(x, y);
        if (isStringLike(a) && isStringLike(b)) return a.toString().equals(b.toString());
        if (a instanceof Boolean x && b instanceof Boolean y) return x.equals(y);
        return a == b;
    }

    public static boolean looseEquals(Object a, Object b) {
        if (isNullish(a) || isNullish(b)) return isNullish(a) && isNullish(b);
        if (a instanceof Number && isStringLike(b)) return numbersEqual((Number) a, toNumber(b));
        if (isStringLike(a) && b instanceof Number) return numbersEqual(toNumber(a), (Number) b);
        if (a instanceof Boolean) return looseEquals(toNumber(a), b);
        if (b instanceof Boolean) return looseEquals(a, toNumber(b));
        return strictEquals(a, b);
    }

    private static boolean numbersEqual(Number x, Number y) {
        if (isIntegral(x) && isIntegral(y)) return x.longValue() == y.longValue();
        return x.doubleValue() == y.doubleValue();
    }

    public static boolean isStringLike(Object value) {
        return value instanceof CharSequence || value instanceof Character;
    }

    public static boolean isIntegral(Number n) {
        return n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte || n instanceof BigInteger;
    }

    /// JS `Number(value)` coercion; non-numeric input becomes NaN.
    public static Number toNumber(Object value) {
        return switch (value) {
            case null -> 0L;
            case JsUndefined u -> Double.NaN;
            case Number n -> n;
            case Boolean b -> b ? 1L : 0L;
            case CharSequence s -> parseNumber(s.toString().trim());
            case Character c -> parseNumber(c.toString());
            default -> Double.NaN;
        };
    }

    private static Number parseNumber(String text) {
        if (text.isEmpty()) return 0L;
        try {
            return normalize(new BigDecimal(text).doubleValue());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static double toDouble(Object value) {
        return toNumber(value).doubleValue();
    }

    /// Collapses integral doubles back to `Long` so arithmetic stays natural (`6 / 3` is `2`, not `2.0`).
    public static Number normalize(double d) {
        if (d == Math.rint(d) && Math.abs(d) < MAX_SAFE_INTEGER && !(d == 0 && 1 / d < 0)) {
            return (long) d;
        }
        return d;
    }

    /// JS `String(value)`: `null` and `undefined` print as words; integral doubles print without a fraction.
    public static String toJsString(Object value) {
        return switch (value) {
            case null -> "null";
            case JsUndefined u -> "undefined";
            case Double d -> doubleToString(d);
            case Float f -> doubleToString(f.doubleValue());
            case BigDecimal b -> b.stripTrailingZeros().toPlainString();
            case Collection<?> c -> c.stream().map(Js::toJsString).reduce((x, y) -> x + "," + y).orElse("");
            case Signal<?> s -> "[Signal]";
            default -> value.toString();
        };
    }

    /// Angular's `renderStringify`: what interpolation prints. `null` and `undefined` become empty text.
    public static String renderStringify(Object value) {
        if (isNullish(value)) return "";
        if (value instanceof Map<?, ?> || (value != null && value.getClass().isArray())) return toJsString(value);
        return toJsString(value);
    }

    public static String doubleToString(double d) {
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
        if (d == Math.rint(d) && Math.abs(d) < 1e21) return Long.toString((long) d);
        return Double.toString(d);
    }

    public static String typeOf(Object value) {
        return switch (value) {
            case null -> "object";
            case JsUndefined u -> "undefined";
            case Boolean b -> "boolean";
            case Number n -> "number";
            case CharSequence s -> "string";
            case Character c -> "string";
            case JsFunction f -> "function";
            case Signal<?> s -> "function";
            default -> "object";
        };
    }

    public static int compare(Object a, Object b) {
        if (isStringLike(a) && isStringLike(b)) return a.toString().compareTo(b.toString());
        if (a instanceof Comparable<?> && b != null && a.getClass() == b.getClass() && !(a instanceof Number)) {
            @SuppressWarnings("unchecked")
            final var comparable = (Comparable<Object>) a;
            return comparable.compareTo(b);
        }
        final var x = toDouble(a);
        final var y = toDouble(b);
        if (Double.isNaN(x) || Double.isNaN(y)) return Integer.MIN_VALUE;
        return Double.compare(x, y);
    }
}
