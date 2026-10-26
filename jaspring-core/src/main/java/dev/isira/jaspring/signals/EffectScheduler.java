package dev.isira.jaspring.signals;

/// Decides when dirty effects run. Effects never run synchronously when a signal is written;
/// the scheduler flushes them at a safe point (a page turn, or `flush()` in tests).
public interface EffectScheduler {

    void add(SchedulableEffect effect);

    /// Called when `effect` became dirty. Must not run the effect synchronously.
    void schedule(SchedulableEffect effect);

    void remove(SchedulableEffect effect);

    void flush();
}
