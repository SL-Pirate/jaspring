package dev.isira.jaspring.browser.runtime;

import java.util.function.Function;

import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.BrowserPromise;

/// A JavaScript promise plus the Java mapping to apply when it settles. `then` composes the mapping without
/// touching the promise; `Results.promise` chains it once, at the end.
final class JsPromise<T> implements BrowserPromise<T> {

    private final JSObject promise;
    private final Function<JSObject, T> decode;

    JsPromise(JSObject promise, Function<JSObject, T> decode) {
        this.promise = promise;
        this.decode = decode;
    }

    JSObject promise() {
        return promise;
    }

    T decode(JSObject raw) {
        return decode.apply(raw);
    }

    @Override
    public <U> BrowserPromise<U> then(Function<? super T, ? extends U> next) {
        return new JsPromise<>(promise, decode.andThen(next));
    }
}
