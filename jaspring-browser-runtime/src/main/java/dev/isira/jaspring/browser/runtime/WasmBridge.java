package dev.isira.jaspring.browser.runtime;

import java.util.function.Function;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

/// The JavaScript side of the module: installs the dispatcher the loader calls and wraps DOM lookups.
/// `@JS.Export` is not implemented in Web Image yet, so functions are handed to `globalThis` from `main`.
public final class WasmBridge {

    private WasmBridge() {
    }

    /// Installs `globalThis.jaspringWasm.run(id, host, args)`; `dispatch` receives `{id, host, args}` and returns a
    /// `{value}` or `{error}` envelope, or a promise of one.
    @JS(args = {"dispatch"}, value = "globalThis.jaspringWasm = { run: function (id, host, args) { return dispatch({ id: String(id), host: String(host), args: args || [] }); } };")
    public static native void install(Function<JSObject, JSObject> dispatch);

    @JS(args = {"selector"}, value = "return document.querySelector(selector);")
    @JS.Coerce
    static native JSObject querySelector(String selector);

    @JS(args = {"element", "selector"}, value = "return element.querySelector(selector);")
    @JS.Coerce
    static native JSObject querySelector(JSObject element, String selector);

    @JS(args = {"element", "selector"}, value = "return Array.from(element.querySelectorAll(selector));")
    @JS.Coerce
    static native JSObject querySelectorAll(JSObject element, String selector);

    @JS(args = {"value"}, value = "return value === null || value === undefined;")
    @JS.Coerce
    static native boolean isNullish(JSObject value);
}
