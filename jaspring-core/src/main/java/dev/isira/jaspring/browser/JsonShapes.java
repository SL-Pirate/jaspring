package dev.isira.jaspring.browser;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The shapes that cross the server/browser boundary as JSON, and the conversion of untyped JSON (maps, lists,
/// numbers, strings, booleans) back into them. The Gradle plugin applies the same rules to class-file descriptors.
///
/// Allowed at the top level (captured values, closure results): primitives and their boxes, `String`, `Void`,
/// enums, records of allowed types, and arrays of allowed types. Inside records also `List<T>`, `Map<String, T>`
/// and `Optional<T>`. Erasure leaves no element type at the lambda boundary, which is why collections need a record.
public final class JsonShapes {

    private static final Set<Class<?>> SCALARS = Set.of(String.class, Boolean.class, Character.class, Byte.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class, Void.class);

    private JsonShapes() {
    }

    /// Why `type` may not cross the boundary, or empty when it may.
    public static Optional<String> rejection(Type type, boolean topLevel) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isPrimitive() || SCALARS.contains(clazz) || clazz.isEnum()) return Optional.empty();
            if (clazz.isArray()) return rejection(clazz.getComponentType(), topLevel).map(reason -> clazz.getSimpleName() + ": " + reason);
            if (clazz.isRecord()) {
                for (final var component : clazz.getRecordComponents()) {
                    final var reason = rejection(component.getGenericType(), false);
                    if (reason.isPresent()) return Optional.of(clazz.getSimpleName() + "." + component.getName() + ": " + reason.get());
                }
                return Optional.empty();
            }
            if (List.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz) || Optional.class == clazz) {
                return Optional.of("a collection at the top level loses its element type to erasure and cannot cross the browser boundary; declare a record");
            }
            return Optional.of(clazz.getName() + " cannot cross the browser boundary; use a record, enum, String, number, boolean or array");
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            if (topLevel) return Optional.of(raw.getSimpleName() + " at the top level loses its type argument to erasure; wrap it in a record");
            final var arguments = parameterized.getActualTypeArguments();
            if (raw == List.class || raw == Optional.class) return rejection(arguments[0], false);
            if (raw == Map.class) {
                if (arguments[0] != String.class) return Optional.of("Map keys must be String");
                return rejection(arguments[1], false);
            }
        }
        return Optional.of(type.getTypeName() + " cannot cross the browser boundary");
    }

    /// Converts JSON as the transport decoded it into `target`. Null stays null except for primitives, which fail.
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object json, Type target) {
        return (T) convertValue(json, target);
    }

    private static Object convertValue(Object json, Type target) {
        if (target instanceof Class<?> clazz) {
            if (json == null) {
                if (clazz.isPrimitive()) throw new IllegalArgumentException("null cannot become " + clazz.getName());
                return null;
            }
            if (clazz == Void.class) return null;
            if (clazz == Object.class) return json;
            if (clazz == String.class) return json.toString();
            if (clazz == boolean.class || clazz == Boolean.class) return json instanceof Boolean b ? b : Boolean.parseBoolean(json.toString());
            if (clazz == char.class || clazz == Character.class) return json.toString().isEmpty() ? '\0' : json.toString().charAt(0);
            if (clazz.isPrimitive() || Number.class.isAssignableFrom(clazz)) return number(json, clazz);
            if (clazz.isEnum()) return enumValue(clazz, json.toString());
            if (clazz.isArray()) return array(json, clazz.getComponentType());
            if (clazz.isRecord()) return record(json, clazz);
            if (clazz.isInstance(json)) return json;
            throw new IllegalArgumentException("Cannot convert " + json.getClass().getSimpleName() + " to " + clazz.getName());
        }
        if (target instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            final var arguments = parameterized.getActualTypeArguments();
            if (raw == Optional.class) return Optional.ofNullable(convertValue(json, arguments[0]));
            if (json == null) return null;
            if (raw == List.class) return list(json, arguments[0]);
            if (raw == Map.class) return map(json, arguments[1]);
        }
        throw new IllegalArgumentException("Unsupported target type " + target.getTypeName());
    }

    private static Object number(Object json, Class<?> clazz) {
        final var number = json instanceof Number n ? n : new BigDecimal(json.toString());
        if (clazz == int.class || clazz == Integer.class) return number.intValue();
        if (clazz == long.class || clazz == Long.class) return number.longValue();
        if (clazz == double.class || clazz == Double.class) return number.doubleValue();
        if (clazz == float.class || clazz == Float.class) return number.floatValue();
        if (clazz == short.class || clazz == Short.class) return number.shortValue();
        if (clazz == byte.class || clazz == Byte.class) return number.byteValue();
        if (clazz == BigDecimal.class) return number instanceof BigDecimal d ? d : new BigDecimal(number.toString());
        if (clazz == BigInteger.class) return number instanceof BigInteger i ? i : new BigDecimal(number.toString()).toBigInteger();
        throw new IllegalArgumentException("Cannot convert number to " + clazz.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> clazz, String name) {
        return Enum.valueOf((Class<? extends Enum>) clazz, name);
    }

    private static Object array(Object json, Class<?> componentType) {
        final var items = json instanceof List<?> list ? list : List.of(json);
        final var array = Array.newInstance(componentType, items.size());
        for (var i = 0; i < items.size(); i++) {
            Array.set(array, i, convertValue(items.get(i), componentType));
        }
        return array;
    }

    private static Object list(Object json, Type elementType) {
        final var items = json instanceof List<?> list ? list : List.of(json);
        final var out = new ArrayList<>(items.size());
        for (final var item : items) {
            out.add(convertValue(item, elementType));
        }
        return List.copyOf(out.stream().map(v -> v == null ? NULL : v).toList()).stream().map(v -> v == NULL ? null : v).toList();
    }

    private static final Object NULL = new Object();

    private static Object map(Object json, Type valueType) {
        if (!(json instanceof Map<?, ?> source)) throw new IllegalArgumentException("Expected an object for a Map, got " + json);
        final var out = new LinkedHashMap<String, Object>();
        for (final var entry : source.entrySet()) {
            out.put(String.valueOf(entry.getKey()), convertValue(entry.getValue(), valueType));
        }
        return out;
    }

    private static Object record(Object json, Class<?> clazz) {
        if (!(json instanceof Map<?, ?> source)) throw new IllegalArgumentException("Expected an object for " + clazz.getSimpleName() + ", got " + json);
        final var components = clazz.getRecordComponents();
        final var types = new Class<?>[components.length];
        final var values = new Object[components.length];
        for (var i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = convertValue(source.get(components[i].getName()), components[i].getGenericType());
        }
        try {
            final var constructor = clazz.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot construct " + clazz.getName(), e);
        }
    }
}
