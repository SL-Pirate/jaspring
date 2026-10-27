package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.BrowserPromise;
import dev.isira.jaspring.browser.Clipboard;

final class NavigatorClipboard implements Clipboard {

    @Override
    public BrowserPromise<Void> writeText(String text) {
        return new JsPromise<>(write(text), ignored -> null);
    }

    @Override
    public BrowserPromise<String> readText() {
        return new JsPromise<>(read(), raw -> Decoders.string(raw, "text"));
    }

    @JS(args = {"text"}, value = "return navigator.clipboard.writeText(text).then(function () { return {}; });")
    @JS.Coerce
    private static native JSObject write(String text);

    @JS(value = "return navigator.clipboard.readText().then(function (text) { return { text: text }; });")
    private static native JSObject read();
}
