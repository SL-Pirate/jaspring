package dev.isira.jaspring.signals;

/// Derives a linked signal's value from its source. `previous` is `null` on the first computation
/// and after the signal was in an error state.
@FunctionalInterface
public interface LinkedComputation<S, T> {

    T compute(S source, Previous<S, T> previous);

    record Previous<S, T>(S source, T value) {}
}
