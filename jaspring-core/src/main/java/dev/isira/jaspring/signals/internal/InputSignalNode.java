package dev.isira.jaspring.signals.internal;

import java.util.Objects;
import java.util.function.Function;

import dev.isira.jaspring.signals.InputSignal;
import dev.isira.jaspring.signals.RequiredInputException;

public final class InputSignalNode<T> extends ReactiveNode implements InputSignal<T> {

    private static final Object REQUIRED_UNSET = new Object();

    private Object value;
    private final Function<Object, T> transform;
    private String name = "input";

    private InputSignalNode(Object initial, Function<Object, T> transform) {
        super(false, false, false);
        this.value = initial;
        this.transform = transform;
    }

    public static <T> InputSignalNode<T> optional(T initial, Function<Object, T> transform) {
        return new InputSignalNode<>(initial, transform);
    }

    public static <T> InputSignalNode<T> required(Function<Object, T> transform) {
        return new InputSignalNode<>(REQUIRED_UNSET, transform);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        Graph.producerAccessed(this);
        if (value == REQUIRED_UNSET) {
            throw new RequiredInputException(name);
        }
        return (T) value;
    }

    /// Called by the renderer when the parent template binds a value; never by component code.
    @SuppressWarnings("unchecked")
    public void applyValue(Object raw) {
        final var next = transform == null ? (T) raw : transform.apply(raw);
        if (value != REQUIRED_UNSET && Objects.equals(value, next)) return;
        value = next;
        version++;
        Graph.producerIncrementEpoch();
        Graph.producerNotifyConsumers(this);
    }

    public boolean isRequired() {
        return value == REQUIRED_UNSET;
    }

    public void bindName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "[InputSignal " + name + ": " + (value == REQUIRED_UNSET ? "<required>" : value) + "]";
    }
}
