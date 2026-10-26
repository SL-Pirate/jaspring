package dev.isira.jaspring.template.eval;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/// Resolves a method parameter's type variable against the receiver's declared generic type, so that
/// `count.set(count() + 1)` on a `WritableSignal<Integer>` field stores an `Integer`, not the evaluator's `Long`.
final class GenericTypes {

    private GenericTypes() {}

    /// The parameter's declared type with type variables substituted from the receiver, e.g. `UnaryOperator<Integer>`.
    static Type parameterGenericType(Type receiverType, Method method, int index) {
        return substitute(method.getGenericParameterTypes()[index], receiverType, method.getDeclaringClass());
    }

    static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return Object.class;
    }

    /// The return type of a functional interface's single abstract method, resolved against the interface's type arguments.
    static Class<?> functionReturnType(Type functionalType) {
        final var raw = rawClass(functionalType);
        for (final var method : raw.getMethods()) {
            if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                return rawClass(substitute(method.getGenericReturnType(), functionalType, method.getDeclaringClass()));
            }
        }
        return Object.class;
    }

    private static Type substitute(Type type, Type context, Class<?> declaring) {
        if (type instanceof TypeVariable<?> variable) {
            final var resolved = context == null ? null : resolveVariable(context, declaring, variable);
            return resolved == null || resolved instanceof TypeVariable<?> ? type : resolved;
        }
        if (type instanceof ParameterizedType parameterized) {
            final var arguments = parameterized.getActualTypeArguments();
            final var substituted = new Type[arguments.length];
            for (var i = 0; i < arguments.length; i++) {
                substituted[i] = substitute(arguments[i], context, declaring);
            }
            return new Parameterized(parameterized.getRawType(), substituted);
        }
        return type;
    }

    private record Parameterized(Type rawType, Type[] arguments) implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    /// The first type argument of `type` when it is parameterized with a plain class, else `null`.
    static Class<?> firstTypeArgument(Type type) {
        if (type instanceof ParameterizedType p && p.getActualTypeArguments()[0] instanceof Class<?> c) return c;
        return null;
    }

    /// `context` is what the receiver was declared as (`WritableSignal<Integer>`); `declaring` is the class that
    /// declares the method (`SignalNode<T>`). Its supertypes bind `WritableSignal.T := SignalNode.T`, so inverting
    /// those bindings links the method's own variable to the declared argument.
    private static Type resolveVariable(Type context, Class<?> declaring, TypeVariable<?> variable) {
        final var bindings = new HashMap<TypeVariable<?>, Type>();
        collect(context, bindings);
        if (declaring != null) {
            final var runtime = new HashMap<TypeVariable<?>, Type>();
            collect(declaring, runtime);
            runtime.forEach((supertypeVariable, bound) -> {
                if (bound instanceof TypeVariable<?> declaringVariable) bindings.putIfAbsent(declaringVariable, supertypeVariable);
            });
        }
        var resolved = bindings.get(variable);
        var hops = 0;
        while (resolved instanceof TypeVariable<?> next && bindings.containsKey(next) && hops++ < 32) {
            resolved = bindings.get(next);
        }
        return resolved;
    }

    private static void collect(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            final var variables = raw.getTypeParameters();
            final var arguments = parameterized.getActualTypeArguments();
            for (var i = 0; i < variables.length; i++) {
                bindings.putIfAbsent(variables[i], arguments[i]);
            }
            collectSupertypes(raw, bindings);
        } else if (type instanceof Class<?> raw) {
            collectSupertypes(raw, bindings);
        }
    }

    private static void collectSupertypes(Class<?> raw, Map<TypeVariable<?>, Type> bindings) {
        for (final var supertype : raw.getGenericInterfaces()) {
            collect(supertype, bindings);
        }
        if (raw.getGenericSuperclass() != null) {
            collect(raw.getGenericSuperclass(), bindings);
        }
    }
}
