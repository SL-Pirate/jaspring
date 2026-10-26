package dev.isira.jaspring.zone;

import java.util.concurrent.atomic.AtomicInteger;

/// Counts work the page must wait for before it is stable, like Angular's `PendingTasks`.
/// `add()` returns the callback that marks the task done.
public final class PendingTasks {

    private final AtomicInteger count = new AtomicInteger();
    private final Runnable onEmpty;

    PendingTasks(Runnable onEmpty) {
        this.onEmpty = onEmpty;
    }

    public Runnable add() {
        count.incrementAndGet();
        final var done = new AtomicInteger();
        return () -> {
            if (done.compareAndSet(0, 1) && count.decrementAndGet() == 0) onEmpty.run();
        };
    }

    public boolean hasPending() {
        return count.get() > 0;
    }

    public int count() {
        return count.get();
    }
}
