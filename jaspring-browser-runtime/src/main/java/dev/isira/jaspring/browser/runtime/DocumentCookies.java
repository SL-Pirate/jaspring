package dev.isira.jaspring.browser.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.graalvm.webimage.api.JS;

import dev.isira.jaspring.browser.Cookies;

/// `document.cookie`, parsed in Java.
final class DocumentCookies implements Cookies {

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(all().get(name));
    }

    @Override
    public Map<String, String> all() {
        final var out = new LinkedHashMap<String, String>();
        for (final var pair : read().split(";")) {
            final var trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            final var equals = trimmed.indexOf('=');
            if (equals < 0) out.put(trimmed, "");
            else out.put(trimmed.substring(0, equals), decode(trimmed.substring(equals + 1)));
        }
        return out;
    }

    @Override
    public void set(String name, String value, CookieOptions options) {
        final var cookie = new StringBuilder(name).append('=').append(encode(value));
        if (options.path() != null) cookie.append("; path=").append(options.path());
        if (options.maxAgeSeconds() != null) cookie.append("; max-age=").append(options.maxAgeSeconds());
        if (options.secure()) cookie.append("; secure");
        if (options.sameSite() != null) cookie.append("; samesite=").append(options.sameSite());
        write(cookie.toString());
    }

    @Override
    public void remove(String name) {
        write(name + "=; path=/; max-age=0");
    }

    @JS(value = "return document.cookie;")
    @JS.Coerce
    private static native String read();

    @JS(args = {"cookie"}, value = "document.cookie = cookie;")
    @JS.Coerce
    private static native void write(String cookie);

    @JS(args = {"value"}, value = "return encodeURIComponent(value);")
    @JS.Coerce
    private static native String encode(String value);

    @JS(args = {"value"}, value = "try { return decodeURIComponent(value); } catch (e) { return value; }")
    @JS.Coerce
    private static native String decode(String value);
}
