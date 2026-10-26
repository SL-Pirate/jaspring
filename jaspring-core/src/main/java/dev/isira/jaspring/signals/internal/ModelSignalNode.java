package dev.isira.jaspring.signals.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import dev.isira.jaspring.signals.ModelSignal;
import dev.isira.jaspring.signals.RequiredInputException;
import dev.isira.jaspring.signals.SignalOwner;
import dev.isira.jaspring.signals.Subscription;

public final class ModelSignalNode<T> extends ReactiveNode implements ModelSignal<T> {

    private static final Object REQUIRED_UNSET = new Object();

    private Object value;
    private final SignalOwner owner;
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private String name = "model";

    private ModelSignalNode(Object initial, SignalOwner owner) {
        super(false, false, false);
        this.value = initial;
        this.owner = owner;
    }

    public static <T> ModelSignalNode<T> optional(T initial, SignalOwner owner) {
        return new ModelSignalNode<>(initial, owner);
    }

    public static <T> ModelSignalNode<T> required(SignalOwner owner) {
        return new ModelSignalNode<>(REQUIRED_UNSET, owner);
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

    /// Component-side write: changes the value and notifies the parent, like Angular's `model.set`.
    @Override
    public void set(T newValue) {
        SignalNode.checkWriteAllowed(owner);
        if (store(newValue)) {
            for (final var listener : List.copyOf(listeners)) {
                listener.accept(newValue);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void update(UnaryOperator<T> updater) {
        if (value == REQUIRED_UNSET) {
            throw new RequiredInputException(name);
        }
        set(updater.apply((T) value));
    }

    /// Parent-side write from the template binding: updates the value without emitting back to the parent.
    public void applyValue(Object raw) {
        store(raw);
    }

    private boolean store(Object newValue) {
        if (value != REQUIRED_UNSET && Objects.equals(value, newValue)) return false;
        value = newValue;
        version++;
        Graph.producerIncrementEpoch();
        Graph.producerNotifyConsumers(this);
        return true;
    }

    @Override
    public Subscription subscribe(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void bindName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "[ModelSignal " + name + ": " + (value == REQUIRED_UNSET ? "<required>" : value) + "]";
    }
}
