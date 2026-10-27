package dev.isira.jaspring.browser.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.Element;
import dev.isira.jaspring.browser.ElementBounds;
import dev.isira.jaspring.browser.HostElement;

/// `Element` over a DOM node.
class DomElement implements Element {

    final JSObject node;

    DomElement(JSObject node) {
        this.node = node;
    }

    static final class Host extends DomElement implements HostElement {
        private final long viewId;

        Host(JSObject node, long viewId) {
            super(node);
            this.viewId = viewId;
        }

        @Override
        public long viewId() {
            return viewId;
        }
    }

    @Override
    public Optional<Element> query(String selector) {
        final var found = WasmBridge.querySelector(node, selector);
        return WasmBridge.isNullish(found) ? Optional.empty() : Optional.of(new DomElement(found));
    }

    @Override
    public List<Element> queryAll(String selector) {
        final var found = WasmBridge.querySelectorAll(node, selector);
        final var out = new ArrayList<Element>();
        for (var i = 0; i < Decoders.length(found); i++) {
            out.add(new DomElement(Decoders.object(found, Decoders.index(i))));
        }
        return out;
    }

    @Override
    public Optional<String> attribute(String name) {
        return hasAttribute(node, name) ? Optional.of(getAttribute(node, name)) : Optional.empty();
    }

    @Override
    public void attribute(String name, String value) {
        setAttribute(node, name, value);
    }

    @Override
    public void removeAttribute(String name) {
        removeAttribute(node, name);
    }

    @Override
    public boolean hasClass(String name) {
        return hasClass(node, name);
    }

    @Override
    public void addClass(String... names) {
        for (final var name : names) {
            toggleClass(node, name, true);
        }
    }

    @Override
    public void removeClass(String... names) {
        for (final var name : names) {
            toggleClass(node, name, false);
        }
    }

    @Override
    public void toggleClass(String name, boolean on) {
        toggleClass(node, name, on);
    }

    @Override
    public String text() {
        return textContent(node);
    }

    @Override
    public void text(String text) {
        textContent(node, text);
    }

    @Override
    public String value() {
        return value(node);
    }

    @Override
    public void value(String value) {
        value(node, value);
    }

    @Override
    public void focus() {
        call(node, "focus");
    }

    @Override
    public void blur() {
        call(node, "blur");
    }

    @Override
    public void scrollIntoView() {
        call(node, "scrollIntoView");
    }

    @Override
    public ElementBounds bounds() {
        final var rect = bounds(node);
        return new ElementBounds(Decoders.doubleValue(rect, "x"), Decoders.doubleValue(rect, "y"),
                Decoders.doubleValue(rect, "width"), Decoders.doubleValue(rect, "height"));
    }

    @Override
    public void dispatch(String type) {
        dispatch(node, type, null);
    }

    @Override
    public void dispatch(String type, String detail) {
        dispatch(node, type, detail);
    }

    @JS(args = {"node", "name"}, value = "return node.hasAttribute(name);")
    @JS.Coerce
    private static native boolean hasAttribute(JSObject node, String name);

    @JS(args = {"node", "name"}, value = "return node.getAttribute(name);")
    @JS.Coerce
    private static native String getAttribute(JSObject node, String name);

    @JS(args = {"node", "name", "value"}, value = "node.setAttribute(name, value);")
    @JS.Coerce
    private static native void setAttribute(JSObject node, String name, String value);

    @JS(args = {"node", "name"}, value = "node.removeAttribute(name);")
    @JS.Coerce
    private static native void removeAttribute(JSObject node, String name);

    @JS(args = {"node", "name"}, value = "return node.classList.contains(name);")
    @JS.Coerce
    private static native boolean hasClass(JSObject node, String name);

    @JS(args = {"node", "name", "on"}, value = "node.classList.toggle(name, on);")
    @JS.Coerce
    private static native void toggleClass(JSObject node, String name, boolean on);

    @JS(args = {"node"}, value = "return node.textContent;")
    @JS.Coerce
    private static native String textContent(JSObject node);

    @JS(args = {"node", "text"}, value = "node.textContent = text;")
    @JS.Coerce
    private static native void textContent(JSObject node, String text);

    @JS(args = {"node"}, value = "return 'value' in node ? String(node.value) : '';")
    @JS.Coerce
    private static native String value(JSObject node);

    @JS(args = {"node", "value"}, value = "if ('value' in node) node.value = value;")
    @JS.Coerce
    private static native void value(JSObject node, String value);

    @JS(args = {"node", "method"}, value = "if (typeof node[method] === 'function') node[method]();")
    @JS.Coerce
    private static native void call(JSObject node, String method);

    @JS(args = {"node"}, value = "var r = node.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height };")
    private static native JSObject bounds(JSObject node);

    @JS(args = {"node", "type", "detail"}, value = "node.dispatchEvent(new CustomEvent(type, { bubbles: true, detail: detail === null ? undefined : detail }));")
    @JS.Coerce
    private static native void dispatch(JSObject node, String type, String detail);
}
