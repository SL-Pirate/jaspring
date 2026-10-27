package dev.isira.jaspring.browser.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.webimage.api.JS;

import dev.isira.jaspring.browser.Storage;

/// `localStorage` (`local == true`) or `sessionStorage`.
final class WebStorage implements Storage {

    private final boolean local;

    WebStorage(boolean local) {
        this.local = local;
    }

    @Override
    public Optional<String> get(String key) {
        return has(local, key) ? Optional.of(getItem(local, key)) : Optional.empty();
    }

    @Override
    public void set(String key, String value) {
        setItem(local, key, value);
    }

    @Override
    public void remove(String key) {
        removeItem(local, key);
    }

    @Override
    public void clear() {
        clear(local);
    }

    @Override
    public List<String> keys() {
        final var out = new ArrayList<String>();
        for (var i = 0; i < length(local); i++) {
            out.add(key(local, i));
        }
        return out;
    }

    @JS(args = {"local", "key"}, value = "return (local ? localStorage : sessionStorage).getItem(key) !== null;")
    @JS.Coerce
    private static native boolean has(boolean local, String key);

    @JS(args = {"local", "key"}, value = "return (local ? localStorage : sessionStorage).getItem(key);")
    @JS.Coerce
    private static native String getItem(boolean local, String key);

    @JS(args = {"local", "key", "value"}, value = "(local ? localStorage : sessionStorage).setItem(key, value);")
    @JS.Coerce
    private static native void setItem(boolean local, String key, String value);

    @JS(args = {"local", "key"}, value = "(local ? localStorage : sessionStorage).removeItem(key);")
    @JS.Coerce
    private static native void removeItem(boolean local, String key);

    @JS(args = {"local"}, value = "(local ? localStorage : sessionStorage).clear();")
    @JS.Coerce
    private static native void clear(boolean local);

    @JS(args = {"local"}, value = "return (local ? localStorage : sessionStorage).length;")
    @JS.Coerce
    private static native int length(boolean local);

    @JS(args = {"local", "index"}, value = "return (local ? localStorage : sessionStorage).key(index);")
    @JS.Coerce
    private static native String key(boolean local, int index);
}
