package dev.isira.jaspring.signals.internal;

/// A producer and/or consumer in the reactive graph. Port of Angular's `ReactiveNode`.
///
/// Not thread-safe: a graph is mutated by one thread at a time (one turn per page). Internal API.
public abstract class ReactiveNode {

    long version;
    long lastCleanEpoch;
    boolean dirty;
    boolean recomputing;
    ReactiveLink producers;
    ReactiveLink producersTail;
    ReactiveLink consumers;
    ReactiveLink consumersTail;
    boolean consumerAllowSignalWrites;
    final boolean consumerIsAlwaysLive;

    protected ReactiveNode(boolean consumerIsAlwaysLive, boolean consumerAllowSignalWrites, boolean dirty) {
        this.consumerIsAlwaysLive = consumerIsAlwaysLive;
        this.consumerAllowSignalWrites = consumerAllowSignalWrites;
        this.dirty = dirty;
    }

    public final boolean isDirty() {
        return dirty;
    }

    public final void clearDirty() {
        dirty = false;
    }

    public final long version() {
        return version;
    }

    /// Whether the value must be recomputed regardless of what producers report (for example when it was never computed).
    protected boolean producerMustRecompute() {
        return false;
    }

    protected void producerRecomputeValue() {}

    /// Called after this consumer was marked dirty by a producer, while the graph is still in its notification phase.
    protected void consumerMarkedDirty() {}

    protected void consumerOnSignalRead(ReactiveNode producer) {}
}
