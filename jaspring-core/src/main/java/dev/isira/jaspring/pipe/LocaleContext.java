package dev.isira.jaspring.pipe;

import java.util.Locale;
import java.util.function.Supplier;

import dev.isira.jaspring.context.CurrentContext;

/// The locale formatting pipes use. The starter binds it per request from Spring's `LocaleContextHolder`;
/// outside a binding it is `en-US`, Angular's default.
public final class LocaleContext {

    private static final CurrentContext<Locale> CURRENT = CurrentContext.threadLocal();

    private LocaleContext() {}

    public static Locale current() {
        final var locale = CURRENT.get();
        return locale == null ? Locale.US : locale;
    }

    public static <T> T runWith(Locale locale, Supplier<T> body) {
        return CURRENT.runWith(locale, body);
    }

    public static Locale parse(String tag) {
        return tag == null || tag.isEmpty() ? current() : Locale.forLanguageTag(tag.replace('_', '-'));
    }
}
