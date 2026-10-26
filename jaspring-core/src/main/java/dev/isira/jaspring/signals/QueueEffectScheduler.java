package dev.isira.jaspring.signals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// FIFO scheduler ported from Angular's `ZoneAwareEffectScheduler`, minus the per-zone queues.
public class QueueEffectScheduler implements EffectScheduler {

    private final Set<SchedulableEffect> queue = new LinkedHashSet<>();
    private int dirtyCount;

    @Override
    public void add(SchedulableEffect effect) {
        queue.add(effect);
        schedule(effect);
    }

    @Override
    public void schedule(SchedulableEffect effect) {
        if (!effect.isDirty()) return;
        dirtyCount++;
    }

    @Override
    public void remove(SchedulableEffect effect) {
        if (!queue.remove(effect)) return;
        if (effect.isDirty()) dirtyCount--;
    }

    public boolean hasDirtyEffects() {
        return dirtyCount > 0;
    }

    @Override
    public void flush() {
        while (dirtyCount > 0) {
            var ranOne = false;
            // Effects may register new effects while running; the outer loop picks those up.
            for (final var effect : List.copyOf(queue)) {
                if (!effect.isDirty()) continue;
                dirtyCount--;
                ranOne = true;
                effect.run();
            }
            // Guards against the count drifting from the dirty flags.
            if (!ranOne) dirtyCount = 0;
        }
    }
}
