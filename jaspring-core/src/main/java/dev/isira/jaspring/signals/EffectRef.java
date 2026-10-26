package dev.isira.jaspring.signals;

public interface EffectRef {

    /// Disconnects the effect from the graph, runs its pending cleanups and removes it from its scheduler.
    void destroy();
}
