package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;

import dev.isira.jaspring.browser.Location;

final class WindowLocation implements Location {

    @Override
    public String href() {
        return read("href");
    }

    @Override
    public String pathname() {
        return read("pathname");
    }

    @Override
    public String search() {
        return read("search");
    }

    @Override
    public String hash() {
        return read("hash");
    }

    @Override
    public void hash(String fragment) {
        setHash(fragment);
    }

    @JS(args = {"property"}, value = "return String(window.location[property]);")
    @JS.Coerce
    private static native String read(String property);

    @JS(args = {"fragment"}, value = "window.location.hash = fragment;")
    @JS.Coerce
    private static native void setHash(String fragment);
}
