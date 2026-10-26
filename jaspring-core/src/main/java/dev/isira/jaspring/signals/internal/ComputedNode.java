package dev.isira.jaspring.signals.internal;

import java.util.function.Supplier;

import dev.isira.jaspring.signals.Equality;
import dev.isira.jaspring.signals.Signal;

public final class ComputedNode<T> extends ReactiveNode implements Signal<T> {

    static final Object UNSET = new Object();
    static final Object COMPUTING = new Object();
    static final Object ERRORED = new Object();

    private Object value = UNSET;
    private RuntimeException error;
    private final Supplier<T> computation;
    private final Equality<T> equality;

    public ComputedNode(Supplier<T> computation, Equality<T> equality) {
        super(false, false, true);
        this.computation = computation;
        this.equality = equality;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        Graph.producerUpdateValueVersion(this);
        Graph.producerAccessed(this);
        if (value == ERRORED) {
            throw error;
        }
        return (T) value;
    }

    @Override
    protected boolean producerMustRecompute() {
        return value == UNSET || value == COMPUTING;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void producerRecomputeValue() {
        if (value == COMPUTING) {
            throw new IllegalStateException("Detected cycle in computations.");
        }
        final var oldValue = value;
        value = COMPUTING;

        final var outcome = Graph.computeAs(this, () -> {
            try {
                final var newValue = computation.get();
                // Equality must not record reactive reads, but an exception from it still poisons the node.
                final var wasEqual = Graph.untracked(() ->
                        oldValue != UNSET && oldValue != ERRORED && equality.equal((T) oldValue, newValue));
                return new Outcome(newValue, wasEqual, null);
            } catch (RuntimeException e) {
                return new Outcome(ERRORED, false, e);
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
        return "[Computed: " + (value == UNSET || value == COMPUTING || value == ERRORED ? "?" : value) + "]";
    }

    record Outcome(Object value, boolean wasEqual, RuntimeException error) {}
}
