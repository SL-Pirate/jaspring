package dev.isira.jaspring.browser.runtime;

import java.util.function.Function;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.BrowserPromise;

/// The `{value}` / `{error}` envelopes the loader turns into `result` messages. Encoded values arrive boxed
/// (`{v: ...}`, see `Encoders.boxed`) so scalars and objects take the same path.
public final class Results {

    private Results() {
    }

    @JS(args = {"boxed"}, value = "return { value: boxed.v };")
    public static native JSObject value(JSObject boxed);

    @JS(args = {"message"}, value = "return { error: message };")
    @JS.Coerce
    public static native JSObject error(String message);

    /// A closure that returned a promise: resolve it in JavaScript, encode the Java value, envelope it. The Java
    /// mapping composed by `then` runs when the promise settles.
    public static JSObject promise(BrowserPromise<?> promise, Function<Object, JSObject> encode) {
        if (promise instanceof BrowserPromise.Resolved<?> resolved) {
            return value(encode.apply(resolved.value()));
        }
        if (promise instanceof JsPromise<?> js) {
            return chain(js.promise(), raw -> {
                try {
                    return value(encode.apply(js.decode(raw)));
                } catch (RuntimeException e) {
                    return error(e.toString());
                }
            });
        }
        return error("Unsupported promise " + promise.getClass().getName());
    }

    @JS(args = {"promise", "settle"}, value = "return promise.then(function (v) { return settle(v); }, function (e) { return { error: String(e && e.message ? e.message : e) }; });")
    static native JSObject chain(JSObject promise, Function<JSObject, JSObject> settle);
}
