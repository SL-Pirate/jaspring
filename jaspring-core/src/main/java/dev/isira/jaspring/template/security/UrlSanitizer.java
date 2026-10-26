package dev.isira.jaspring.template.security;

import java.util.regex.Pattern;

/// Port of Angular's `_sanitizeUrl`: anything that is not a known-safe scheme or a relative URL gets the `unsafe:` prefix.
public final class UrlSanitizer {

    private static final Pattern SAFE_URL = Pattern.compile("^(?!javascript:)(?:[a-z0-9+.-]+:|[^&:/?#]*(?:[/?#]|$))", Pattern.CASE_INSENSITIVE);

    private UrlSanitizer() {}

    public static String sanitize(String url) {
        return SAFE_URL.matcher(url).find() ? url : "unsafe:" + url;
    }
}
