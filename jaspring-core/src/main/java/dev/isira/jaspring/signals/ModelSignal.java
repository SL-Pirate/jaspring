package dev.isira.jaspring.signals;

/// Two-way bindable state (`Signals.model()`): writable by the component, and every change is emitted
/// to the parent through the matching `xChange` output.
public interface ModelSignal<T> extends WritableSignal<T>, OutputRef<T> {}
