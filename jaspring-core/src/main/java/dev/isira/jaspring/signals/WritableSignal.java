package dev.isira.jaspring.signals;

import java.util.function.UnaryOperator;

public interface WritableSignal<T> extends Signal<T> {

    void set(T value);

    void update(UnaryOperator<T> updater);

    default Signal<T> asReadonly() {
        return this::get;
    }
}
