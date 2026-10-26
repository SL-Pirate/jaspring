package dev.isira.jaspring.context;

import java.util.function.Supplier;

/// A value bound to the current thread for the dynamic extent of `runWith`.
///
/// Bindings are strictly scoped: there is no `set`, so a `ScopedValue`-backed implementation
/// can replace the ThreadLocal one without changing any caller.
public interface CurrentContext<T> {

    /// The bound value, or `null` outside any `runWith`.
    T get();

    <R> R runWith(T value, Supplier<R> body);

    default void runWith(T value, Runnable body) {
        runWith(value, () -> {
            body.run();
            return null;
        });
    }

    static <T> CurrentContext<T> threadLocal() {
        return new ThreadLocalContext<>();
    }
}
