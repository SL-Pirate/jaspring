package dev.isira.jaspring.signals;

/// A reactive value. Reading it inside a reactive consumer (computed, effect, template) records a dependency.
///
/// Templates call signals like Angular does: `{{ count() }}` evaluates `count.get()`.
@FunctionalInterface
public interface Signal<T> {

    T get();
}
