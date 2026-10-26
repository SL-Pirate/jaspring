package dev.isira.jaspring.component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import dev.isira.jaspring.signals.OutputRef;
import dev.isira.jaspring.signals.Subscription;

/// One event the parent may listen to: an `OutputEmitter` field, or the `xChange` side of a `ModelSignal`.
public final class OutputDef {

    private final String name;
    private final MethodHandle getter;

    OutputDef(String name, Field field) {
        this.name = name;
        try {
            field.setAccessible(true);
            this.getter = MethodHandles.lookup().unreflectGetter(field);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access output field " + field, e);
        }
    }

    public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    public Subscription subscribe(Object instance, Consumer<Object> listener) {
        try {
            return ((OutputRef<Object>) getter.invoke(instance)).subscribe(listener);
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot subscribe to output " + name, t);
        }
    }
}
