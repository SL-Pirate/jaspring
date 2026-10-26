package dev.isira.jaspring.signals.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import dev.isira.jaspring.signals.CleanupRegistrar;
import dev.isira.jaspring.signals.EffectRef;
import dev.isira.jaspring.signals.EffectScheduler;
import dev.isira.jaspring.signals.SchedulableEffect;

/// Port of Angular's root effect node. Starts dirty, is always live, and may write signals.
public final class EffectNode extends ReactiveNode implements SchedulableEffect, EffectRef {

    private final Consumer<CleanupRegistrar> body;
    private final EffectScheduler scheduler;
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private boolean destroyed;

    public EffectNode(Consumer<CleanupRegistrar> body, EffectScheduler scheduler) {
        super(true, true, true);
        this.body = body;
        this.scheduler = scheduler;
    }

    @Override
    protected void consumerMarkedDirty() {
        scheduler.schedule(this);
    }

    @Override
    public void run() {
        if (destroyed) return;
        if (Graph.inNotificationPhase()) {
            throw new IllegalStateException("Schedulers cannot synchronously execute effects while scheduling");
        }
        dirty = false;
        if (version > 0 && !Graph.consumerPollProducersForChange(this)) return;
        version++;
        Graph.computeAs(this, () -> {
            cleanup();
            body.accept(cleanups::push);
            return null;
        });
    }

    private void cleanup() {
        if (cleanups.isEmpty()) return;
        Graph.untracked(() -> {
            try {
                while (!cleanups.isEmpty()) {
                    cleanups.pop().run();
                }
            } finally {
                cleanups.clear();
            }
            return null;
        });
    }

    @Override
    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        Graph.consumerDestroy(this);
        cleanup();
        scheduler.remove(this);
    }
}
