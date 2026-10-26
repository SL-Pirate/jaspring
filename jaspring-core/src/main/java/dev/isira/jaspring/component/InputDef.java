package dev.isira.jaspring.component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import dev.isira.jaspring.signals.internal.InputSignalNode;
import dev.isira.jaspring.signals.internal.ModelSignalNode;

/// One bindable input: a plain field, an `InputSignal` field or a `ModelSignal` field.
public final class InputDef {

    public enum Kind { FIELD, SIGNAL, MODEL }

    private final String name;
    private final Kind kind;
    private final boolean required;
    private final Field field;
    private final MethodHandle getter;
    private final MethodHandle setter;

    InputDef(String name, Kind kind, boolean required, Field field) {
        this.name = name;
        this.kind = kind;
        this.required = required;
        this.field = field;
        try {
            field.setAccessible(true);
            this.getter = MethodHandles.lookup().unreflectGetter(field);
            this.setter = MethodHandles.lookup().unreflectSetter(field);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access input field " + field, e);
        }
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public boolean required() {
        return required;
    }

    public Class<?> type() {
        return field.getType();
    }

    public java.lang.reflect.Type genericType() {
        return field.getGenericType();
    }

    public Object read(Object instance) {
        try {
            return getter.invoke(instance);
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot read input " + name, t);
        }
    }

    /// Writes a parent-bound value. Signal inputs notify their readers; plain fields are set directly.
    public void apply(Object instance, Object value) {
        try {
            switch (kind) {
                case FIELD -> setter.invoke(instance, value);
                case SIGNAL -> ((InputSignalNode<?>) getter.invoke(instance)).applyValue(value);
                case MODEL -> ((ModelSignalNode<?>) getter.invoke(instance)).applyValue(value);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot set input '" + name + "' on " + instance.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }
}
