package dev.isira.jaspring.template.eval;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.isira.jaspring.template.expr.JsUndefined;

/// Reads and writes members of Java objects the way a template expects: fields, getters, record accessors,
/// map keys, `length` and the JavaScript method shim. Reflection is resolved once per class and cached.
public final class MemberAccessor {

    private static final ClassValue<Map<String, Member>> CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Member> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private MemberAccessor() {}

    /// @return the member value, `JsUndefined` when the object has no such member, or a `JsFunction` for methods
    public static Object read(Object target, String name) {
        if (Js.isNullish(target)) {
            throw new EvaluationException("Cannot read properties of " + Js.toJsString(target) + " (reading '" + name + "')");
        }
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(name) ? map.get(name) : JsUndefined.INSTANCE;
        }
        final var shimmed = JsShim.read(target, name);
        if (shimmed != JsShim.NONE) return shimmed;
        final var member = member(target.getClass(), name);
        return member == null ? JsUndefined.INSTANCE : member.read(target);
    }

    /// Like `read`, but a member that has methods resolves to a callable even when a same-named accessor exists,
    /// so `reset()` calls the method instead of reading a property named `reset`.
    public static Object readForCall(Object target, String name, Type targetDeclaredType) {
        if (Js.isNullish(target)) {
            throw new EvaluationException("Cannot read properties of " + Js.toJsString(target) + " (reading '" + name + "')");
        }
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(name) ? map.get(name) : JsUndefined.INSTANCE;
        }
        final var shimmed = JsShim.read(target, name);
        if (shimmed != JsShim.NONE) return shimmed;
        final var member = member(target.getClass(), name);
        if (member == null) return JsUndefined.INSTANCE;
        return member.methods.isEmpty() ? member.read(target) : new BoundMethod(target, name, member.methods, targetDeclaredType);
    }

    /// The declared generic type of a field or accessor, or `null` when unknown.
    public static Type declaredType(Object target, String name) {
        if (Js.isNullish(target) || target instanceof Map<?, ?>) return null;
        final var member = member(target.getClass(), name);
        return member == null ? null : member.genericType;
    }

    public static boolean has(Object target, String name) {
        if (Js.isNullish(target)) return false;
        if (target instanceof Map<?, ?> map) return map.containsKey(name);
        return JsShim.read(target, name) != JsShim.NONE || member(target.getClass(), name) != null;
    }

    public static boolean hasMember(Class<?> type, String name) {
        return member(type, name) != null;
    }

    public static void write(Object target, String name, Object value) {
        if (Js.isNullish(target)) {
            throw new EvaluationException("Cannot set properties of " + Js.toJsString(target) + " (setting '" + name + "')");
        }
        if (target instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            final var writable = (Map<String, Object>) map;
            writable.put(name, value);
            return;
        }
        final var member = member(target.getClass(), name);
        if (member == null || !member.write(target, value)) {
            throw new EvaluationException("Cannot assign to '" + name + "' on " + target.getClass().getSimpleName());
        }
    }

    public static Object readIndex(Object target, Object key) {
        if (Js.isNullish(target)) {
            throw new EvaluationException("Cannot read properties of " + Js.toJsString(target) + " (reading '" + Js.toJsString(key) + "')");
        }
        if (target instanceof List<?> list) {
            final var index = indexOf(key);
            return index != null && index >= 0 && index < list.size() ? list.get(index) : JsUndefined.INSTANCE;
        }
        if (target.getClass().isArray()) {
            final var index = indexOf(key);
            return index != null && index >= 0 && index < Array.getLength(target) ? Array.get(target, index) : JsUndefined.INSTANCE;
        }
        if (target instanceof Map<?, ?> map) {
            if (map.containsKey(key)) return map.get(key);
            final var text = Js.toJsString(key);
            if (map.containsKey(text)) return map.get(text);
            final var index = indexOf(key);
            if (index != null && map.containsKey(index)) return map.get(index);
            return JsUndefined.INSTANCE;
        }
        if (target instanceof CharSequence s) {
            final var index = indexOf(key);
            return index != null && index >= 0 && index < s.length() ? String.valueOf(s.charAt(index)) : JsUndefined.INSTANCE;
        }
        return read(target, Js.toJsString(key));
    }

    public static void writeIndex(Object target, Object key, Object value) {
        if (target instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            final var writable = (List<Object>) list;
            final var index = indexOf(key);
            if (index == null) throw new EvaluationException("Invalid list index " + Js.toJsString(key));
            if (index == writable.size()) writable.add(value); else writable.set(index, value);
            return;
        }
        if (target != null && target.getClass().isArray()) {
            Array.set(target, indexOf(key), value);
            return;
        }
        if (target instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            final var writable = (Map<Object, Object>) map;
            writable.put(map.containsKey(key) ? key : Js.toJsString(key), value);
            return;
        }
        write(target, Js.toJsString(key), value);
    }

    private static Integer indexOf(Object key) {
        if (key instanceof Number n) {
            final var d = n.doubleValue();
            return d == Math.rint(d) ? (int) d : null;
        }
        if (key instanceof CharSequence s) {
            try {
                return Integer.parseInt(s.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static Member member(Class<?> type, String name) {
        final var members = CACHE.get(type);
        var member = members.get(name);
        if (member == null) {
            member = resolve(type, name);
            members.put(name, member == null ? Member.NONE : member);
        }
        return member == Member.NONE ? null : member;
    }

    private static Member resolve(Class<?> type, String name) {
        final var field = findField(type, name);
        final var getter = findMethod(type, "get" + capitalize(name), 0);
        final var isser = findMethod(type, "is" + capitalize(name), 0);
        final var accessor = findMethod(type, name, 0);
        final var setter = findMethod(type, "set" + capitalize(name), 1);
        final var reader = field != null ? field.read : getter != null ? getter : isser != null ? isser : accessor;
        final var methods = Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(name) && !Modifier.isStatic(m.getModifiers()))
                .toList();
        if (reader == null && methods.isEmpty()) return null;
        final var genericType = field != null ? field.genericType
                : getter != null ? getter.genericReturnType : isser != null ? isser.genericReturnType
                : accessor != null ? accessor.genericReturnType : null;
        return new Member(reader == null ? null : reader.handle, field != null && !field.isFinal ? field.write : setter == null ? null : setter.handle,
                methods, name, genericType);
    }

    private static FieldHandles findField(Class<?> type, String name) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            for (final var field : current.getDeclaredFields()) {
                if (!field.getName().equals(name) || Modifier.isStatic(field.getModifiers())) continue;
                if (!Modifier.isPublic(field.getModifiers()) && !isComponentLike(type)) continue;
                try {
                    field.setAccessible(true);
                    final var lookup = MethodHandles.lookup();
                    return new FieldHandles(new Handle(lookup.unreflectGetter(field), field.getGenericType()),
                            lookup.unreflectSetter(field), Modifier.isFinal(field.getModifiers()), field.getGenericType());
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // Component classes expose their own fields to their template like Angular does; other objects only expose public API.
    private static boolean isComponentLike(Class<?> type) {
        return Arrays.stream(type.getAnnotations()).anyMatch(a -> a.annotationType().getSimpleName().equals("Component"));
    }

    private static Handle findMethod(Class<?> type, String name, int arity) {
        for (final var method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity && !Modifier.isStatic(method.getModifiers())) {
                final var handle = unreflect(method);
                return handle == null ? null : new Handle(handle, method.getGenericReturnType());
            }
        }
        return null;
    }

    static MethodHandle unreflect(Method method) {
        try {
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private record FieldHandles(Handle read, MethodHandle write, boolean isFinal, Type genericType) {}

    private record Handle(MethodHandle handle, Type genericReturnType) {}

    /// One resolved member: how to read it, how to write it, the overloads a call can pick from, and its declared type.
    static final class Member {
        static final Member NONE = new Member(null, null, List.of(), "", null);

        private final MethodHandle reader;
        private final MethodHandle writer;
        final List<Method> methods;
        private final String name;
        final Type genericType;

        Member(MethodHandle reader, MethodHandle writer, List<Method> methods, String name, Type genericType) {
            this.reader = reader;
            this.writer = writer;
            this.methods = methods;
            this.name = name;
            this.genericType = genericType;
        }

        Object read(Object target) {
            // Fields, getters and zero-arg accessors (records) read as properties; anything else is a callable.
            if (reader == null) {
                return new BoundMethod(target, name, methods, null);
            }
            try {
                return reader.invoke(target);
            } catch (Throwable t) {
                throw new EvaluationException("Reading '" + name + "' failed: " + t.getMessage(), t);
            }
        }

        boolean write(Object target, Object value) {
            if (writer == null) return false;
            try {
                writer.invoke(target, ArgumentCoercion.coerce(value, writer.type().parameterType(1)));
            } catch (Throwable t) {
                throw new EvaluationException("Assigning '" + name + "' failed: " + t.getMessage(), t);
            }
            return true;
        }
    }

    /// A Java method with its receiver, callable from a template. `receiverType` is the receiver's declared generic
    /// type when it came from a typed member, used to coerce arguments bound to type variables.
    public record BoundMethod(Object target, String name, List<Method> overloads, Type receiverType) implements JsFunction {

        @Override
        public Object call(Object... args) {
            final var candidates = new ArrayList<Method>();
            for (final var method : overloads) {
                if (method.getParameterCount() == args.length && ArgumentCoercion.accepts(method.getParameterTypes(), args)) {
                    candidates.add(method);
                }
            }
            if (candidates.isEmpty()) {
                throw new EvaluationException("No overload of " + name + " accepts " + args.length + " argument(s)");
            }
            final var method = candidates.get(0);
            final var handle = unreflect(method);
            final var coerced = new Object[args.length + 1];
            coerced[0] = target;
            for (var i = 0; i < args.length; i++) {
                coerced[i + 1] = ArgumentCoercion.coerce(args[i], GenericTypes.parameterGenericType(receiverType, method, i));
            }
            try {
                final var result = handle.invokeWithArguments(coerced);
                return method.getReturnType() == void.class ? JsUndefined.INSTANCE : result;
            } catch (EvaluationException e) {
                throw e;
            } catch (Throwable t) {
                throw new EvaluationException(name + "() failed: " + t.getMessage(), t);
            }
        }
    }

}
