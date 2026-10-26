package dev.isira.jaspring.signals;

/// A component input declared as a signal (`Signals.input()`); the parent template sets it, the component reads it.
public interface InputSignal<T> extends Signal<T> {}
