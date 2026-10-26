package dev.isira.jaspring.template.eval;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;

import dev.isira.jaspring.template.expr.JsUndefined;

/// Adapts template values to Java parameter types: JS numbers to the numeric type a method declares,
/// `undefined` to `null`, and arrow functions to functional interfaces.
final class ArgumentCoercion {

    private ArgumentCoercion() {}

    static boolean accepts(Class<?>[] types, Object[] args) {
        for (var i = 0; i < types.length; i++) {
            if (!accepts(types[i], args[i])) return false;
        }
        return true;
    }

    static boolean accepts(Class<?> type, Object arg) {
        if (Js.isNullish(arg)) return !type.isPrimitive();
        if (type.isInstance(arg)) return true;
        if (arg instanceof Number) return isNumeric(type);
        if (arg instanceof Boolean) return type == boolean.class;
        if (arg instanceof JsFunction) return isFunctionalInterface(type);
        if (arg instanceof CharSequence s && (type == char.class || type == Character.class)) return s.length() == 1;
        return false;
    }

    static Object coerce(Object arg, Type type) {
        final var raw = GenericTypes.rawClass(type);
        if (arg instanceof JsFunction fn && !raw.isInstance(arg) && isFunctionalInterface(raw)) {
            return adapt(fn, raw, GenericTypes.functionReturnType(type));
        }
        return coerce(arg, raw);
    }

    static Object coerce(Object arg, Class<?> type) {
        if (arg == JsUndefined.INSTANCE) return null;
        if (arg == null || type.isInstance(arg)) return arg;
        if (arg instanceof Number n) return coerceNumber(n, type);
        if (arg instanceof JsFunction fn && isFunctionalInterface(type)) return adapt(fn, type, Object.class);
        if (arg instanceof CharSequence s && (type == char.class || type == Character.class)) return s.charAt(0);
        return arg;
    }

    private static boolean isNumeric(Class<?> type) {
        return type == int.class || type == long.class || type == double.class || type == float.class
                || type == short.class || type == byte.class || Number.class.isAssignableFrom(type);
    }

    private static Object coerceNumber(Number n, Class<?> type) {
        if (type == int.class || type == Integer.class) return n.intValue();
        if (type == long.class || type == Long.class) return n.longValue();
        if (type == double.class || type == Double.class) return n.doubleValue();
        if (type == float.class || type == Float.class) return n.floatValue();
        if (type == short.class || type == Short.class) return n.shortValue();
        if (type == byte.class || type == Byte.class) return n.byteValue();
        return n;
    }

    static boolean isFunctionalInterface(Class<?> type) {
        return type.isInterface() && Arrays.stream(type.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()) && !isObjectMethod(m.getName()))
                .count() == 1;
    }

    private static boolean isObjectMethod(String name) {
        return name.equals("equals") || name.equals("hashCode") || name.equals("toString");
    }

    private static Object adapt(JsFunction fn, Class<?> type, Class<?> returnType) {
        final InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "[JsFunction]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            if (method.isDefault()) {
                return java.lang.invoke.MethodHandles.lookup()
                        .unreflectSpecial(method, method.getDeclaringClass())
                        .bindTo(proxy)
                        .invokeWithArguments(args == null ? new Object[0] : args);
            }
            final var result = fn.call(args == null ? new Object[0] : args);
            return coerce(result, returnType == Object.class ? method.getReturnType() : returnType);
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
