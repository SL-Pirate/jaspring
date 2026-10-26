package dev.isira.jaspring.signals.internal;

import java.util.function.UnaryOperator;

import dev.isira.jaspring.signals.Equality;
import dev.isira.jaspring.signals.InvalidSignalWriteException;
import dev.isira.jaspring.signals.SignalOwner;
import dev.isira.jaspring.signals.SignalWriteOutsideTurnException;
import dev.isira.jaspring.signals.WritableSignal;

public final class SignalNode<T> extends ReactiveNode implements WritableSignal<T> {

    private T value;
    private final Equality<T> equality;
    private final SignalOwner owner;

    public SignalNode(T initial, Equality<T> equality, SignalOwner owner) {
        super(false, false, false);
        this.value = initial;
        this.equality = equality;
        this.owner = owner;
    }

    @Override
    public T get() {
        Graph.producerAccessed(this);
        return value;
    }

    @Override
    public void set(T newValue) {
        checkWriteAllowed(owner);
        if (!equality.equal(value, newValue)) {
            value = newValue;
            valueChanged();
        }
    }

    @Override
    public void update(UnaryOperator<T> updater) {
        checkWriteAllowed(owner);
        set(updater.apply(value));
    }

    private void valueChanged() {
        version++;
        Graph.producerIncrementEpoch();
        Graph.producerNotifyConsumers(this);
    }

    static void checkWriteAllowed(SignalOwner owner) {
        if (!Graph.producerUpdatesAllowed()) {
            throw new InvalidSignalWriteException();
        }
        if (owner != null && !owner.mayWriteFromCurrentThread()) {
            throw new SignalWriteOutsideTurnException(owner);
        }
    }

    @Override
    public String toString() {
        return "[Signal: " + value + "]";
    }
}
