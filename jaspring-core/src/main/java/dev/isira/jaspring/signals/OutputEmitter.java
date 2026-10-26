package dev.isira.jaspring.signals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A component output (`Signals.output()`); the parent template subscribes through `(name)="..."`.
public final class OutputEmitter<T> implements OutputRef<T> {

    private final List<Consumer<T>> listeners = new ArrayList<>();
    private boolean destroyed;

    public void emit(T value) {
        if (destroyed) {
            throw new IllegalStateException("Output emitted after the component was destroyed");
        }
        for (final var listener : List.copyOf(listeners)) {
            listener.accept(value);
        }
    }

    @Override
    public Subscription subscribe(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void destroy() {
        destroyed = true;
        listeners.clear();
    }
}
