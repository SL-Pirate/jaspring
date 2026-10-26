package dev.isira.jaspring.component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.isira.jaspring.signals.InputSignal;
import dev.isira.jaspring.signals.ModelSignal;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.selector.CssSelector;

/// An attribute directive: its selector, inputs, host bindings and host listeners.
public record DirectiveDef(
        Class<?> type,
        String selector,
        List<CssSelector> selectors,
        Map<String, InputDef> inputs,
        List<HostBindingDef> hostBindings,
        List<HostListenerDef> hostListeners) {

    /// `@HostBinding("class.x")`, `"attr.x"`, `"style.x.unit"` or a property name, read from a field or getter.
    public record HostBindingDef(String target, MethodHandle reader) {
        public Object read(Object instance) {
            try {
                return reader.invoke(instance);
            } catch (Throwable t) {
                throw new IllegalStateException("Host binding " + target + " failed: " + t.getMessage(), t);
            }
        }
    }

    /// `@HostListener("click", ["$event"])`, compiled to a handler expression `$dir.method(args)` that the
    /// event dispatcher evaluates with `$dir` bound to the directive instance.
    public record HostListenerDef(String event, String method, List<String> args, TemplateNode.BoundEvent handler) {
        public String source() {
            return "$dir." + method + "(" + String.join(", ", args) + ")";
        }
    }

    public String name() {
        return type.getSimpleName();
    }

    public static DirectiveDef of(Class<?> type, java.util.function.Function<HostListenerDef, TemplateNode.BoundEvent> listenerCompiler) {
        final var annotation = type.getAnnotation(Directive.class);
        if (annotation == null) throw new IllegalArgumentException(type.getName() + " is not annotated with @Directive");
        final var inputs = new LinkedHashMap<String, InputDef>();
        final var bindings = new ArrayList<HostBindingDef>();
        for (final var field : ComponentDefs.fields(type)) {
            final var input = field.getAnnotation(Input.class);
            if (InputSignal.class.isAssignableFrom(field.getType())) {
                final var name = input != null && !input.value().isEmpty() ? input.value() : field.getName();
                inputs.put(name, new InputDef(name, InputDef.Kind.SIGNAL, false, field));
            } else if (ModelSignal.class.isAssignableFrom(field.getType())) {
                final var name = input != null && !input.value().isEmpty() ? input.value() : field.getName();
                inputs.put(name, new InputDef(name, InputDef.Kind.MODEL, false, field));
            } else if (input != null) {
                final var name = input.value().isEmpty() ? field.getName() : input.value();
                inputs.put(name, new InputDef(name, InputDef.Kind.FIELD, input.required(), field));
            }
            final var hostBinding = field.getAnnotation(HostBinding.class);
            if (hostBinding != null) {
                try {
                    field.setAccessible(true);
                    bindings.add(new HostBindingDef(hostBinding.value().isEmpty() ? field.getName() : hostBinding.value(),
                            MethodHandles.lookup().unreflectGetter(field)));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        final var listeners = new ArrayList<HostListenerDef>();
        for (final var method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            final var hostBinding = method.getAnnotation(HostBinding.class);
            if (hostBinding != null && method.getParameterCount() == 0) {
                try {
                    final var name = hostBinding.value().isEmpty() ? propertyName(method.getName()) : hostBinding.value();
                    bindings.add(new HostBindingDef(name, MethodHandles.lookup().unreflect(method)));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            final var hostListener = method.getAnnotation(HostListener.class);
            if (hostListener != null) {
                final var pending = new HostListenerDef(hostListener.value(), method.getName(), List.of(hostListener.args()), null);
                listeners.add(new HostListenerDef(pending.event(), pending.method(), pending.args(), listenerCompiler.apply(pending)));
            }
        }
        return new DirectiveDef(type, annotation.selector(), CssSelector.parse(annotation.selector()),
                Collections.unmodifiableMap(inputs), List.copyOf(bindings), List.copyOf(listeners));
    }

    private static String propertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        if (methodName.startsWith("is") && methodName.length() > 2) return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        return methodName;
    }

    /// Whether the handler of a host listener was compiled (needs the parser; done in `ComponentRegistry.compileAll`).
    public boolean isCompiled() {
        return hostListeners.stream().allMatch(l -> l.handler() != null);
    }

    public static Expr handlerOf(HostListenerDef listener) {
        return listener.handler().handler();
    }
}
