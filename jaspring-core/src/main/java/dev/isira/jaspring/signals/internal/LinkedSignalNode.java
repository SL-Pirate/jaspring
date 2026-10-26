package dev.isira.jaspring.signals.internal;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import dev.isira.jaspring.signals.Equality;
import dev.isira.jaspring.signals.LinkedComputation;
import dev.isira.jaspring.signals.SignalOwner;
import dev.isira.jaspring.signals.WritableSignal;

/// A writable signal whose value is recomputed from `source` whenever the source changes,
/// but which keeps explicit `set` values until then.
public final class LinkedSignalNode<S, T> extends ReactiveNode implements WritableSignal<T> {

    private Object value = ComputedNode.UNSET;
    private S sourceValue;
    private RuntimeException error;
    private final Supplier<S> source;
    private final LinkedComputation<S, T> computation;
    private final Equality<T> equality;
    private final SignalOwner owner;

    public LinkedSignalNode(Supplier<S> source, LinkedComputation<S, T> computation, Equality<T> equality,
            SignalOwner owner) {
        super(false, false, true);
        this.source = source;
        this.computation = computation;
        this.equality = equality;
        this.owner = owner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        Graph.producerUpdateValueVersion(this);
        Graph.producerAccessed(this);
        if (value == ComputedNode.ERRORED) {
            throw error;
        }
        return (T) value;
    }

    @Override
    public void set(T newValue) {
        Graph.producerUpdateValueVersion(this);
        signalSet(newValue);
        Graph.producerMarkClean(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void update(UnaryOperator<T> updater) {
        Graph.producerUpdateValueVersion(this);
        if (value == ComputedNode.ERRORED) {
            throw error;
        }
        SignalNode.checkWriteAllowed(owner);
        signalSet(updater.apply((T) value));
        Graph.producerMarkClean(this);
    }

    @SuppressWarnings("unchecked")
    private void signalSet(T newValue) {
        SignalNode.checkWriteAllowed(owner);
        final var hasValue = value != ComputedNode.UNSET && value != ComputedNode.ERRORED;
        if (hasValue && equality.equal((T) value, newValue)) return;
        value = newValue;
        version++;
        Graph.producerIncrementEpoch();
        Graph.producerNotifyConsumers(this);
    }

    @Override
    protected boolean producerMustRecompute() {
        return value == ComputedNode.UNSET || value == ComputedNode.COMPUTING;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void producerRecomputeValue() {
        if (value == ComputedNode.COMPUTING) {
            throw new IllegalStateException("Detected cycle in computations.");
        }
        final var oldValue = value;
        value = ComputedNode.COMPUTING;

        final var outcome = Graph.computeAs(this, () -> {
            try {
                final var newSourceValue = source.get();
                final var oldValueValid = oldValue != ComputedNode.UNSET && oldValue != ComputedNode.ERRORED;
                final var previous = oldValueValid
                        ? new LinkedComputation.Previous<>(sourceValue, (T) oldValue)
                        : null;
                final var newValue = computation.compute(newSourceValue, previous);
                sourceValue = newSourceValue;
                final var wasEqual = Graph.untracked(() -> oldValueValid && equality.equal((T) oldValue, newValue));
                return new ComputedNode.Outcome(newValue, wasEqual, null);
            } catch (RuntimeException e) {
                return new ComputedNode.Outcome(ComputedNode.ERRORED, false, e);
            }
        });

        if (outcome.wasEqual()) {
            value = oldValue;
            return;
        }
        value = outcome.value();
        error = outcome.error();
        version++;
    }

    @Override
    public String toString() {
        return "[LinkedSignal: " + value + "]";
    }
}
