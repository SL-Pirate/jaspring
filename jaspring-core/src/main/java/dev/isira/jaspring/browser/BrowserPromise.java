package dev.isira.jaspring.browser;

import java.util.function.Function;

/// A value the browser produces later (a JavaScript promise). Closures passed to `Platform.onBrowserAsync` return
/// one; `then` maps it without leaving the browser. There is no way to wait for it in Java.
public interface BrowserPromise<T> {

    <U> BrowserPromise<U> then(Function<? super T, ? extends U> next);

    /// An already available value, for closures that sometimes do not need to wait.
    static <T> BrowserPromise<T> of(T value) {
        return new Resolved<>(value);
    }

    record Resolved<T>(T value) implements BrowserPromise<T> {
        @Override
        public <U> BrowserPromise<U> then(Function<? super T, ? extends U> next) {
            return new Resolved<>(next.apply(value));
        }
    }
}
