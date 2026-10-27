package dev.isira.jaspring.browser;

import java.util.Map;
import java.util.Optional;

/// `document.cookie`, limited to what JavaScript can see (no `HttpOnly` cookies).
public interface Cookies {

    Optional<String> get(String name);

    Map<String, String> all();

    void set(String name, String value, CookieOptions options);

    void remove(String name);

    record CookieOptions(String path, Integer maxAgeSeconds, boolean secure, String sameSite) {

        public static final CookieOptions DEFAULT = new CookieOptions("/", null, false, "Lax");
    }
}
