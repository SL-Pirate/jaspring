package dev.isira.jaspring.template.security;

import dev.isira.jaspring.template.ast.SecurityContext;
import dev.isira.jaspring.template.eval.Js;

/// Angular's `DomSanitizer`: sanitizes bound values by security context and lets the application bypass it explicitly.
public final class DomSanitizer {

    public static final DomSanitizer INSTANCE = new DomSanitizer();

    private DomSanitizer() {}

    /// @return the string to emit, or `null` when the value is nullish (binding removed)
    public String sanitize(SecurityContext context, Object value) {
        if (Js.isNullish(value)) return null;
        return switch (context) {
            case NONE, ATTRIBUTE_NO_BINDING -> Js.toJsString(value);
            case HTML -> value instanceof SafeValue.SafeHtml safe ? safe.value() : HtmlSanitizer.sanitize(unwrapOrFail(value, context));
            case STYLE -> value instanceof SafeValue.SafeStyle safe ? safe.value() : Js.toJsString(unwrapOrFail(value, context));
            case SCRIPT -> value instanceof SafeValue.SafeScript safe
                    ? safe.value()
                    : throwUnsafe("unsafe value used in a script context");
            case URL -> value instanceof SafeValue.SafeUrl safe ? safe.value() : UrlSanitizer.sanitize(Js.toJsString(unwrapOrFail(value, context)));
            case RESOURCE_URL -> value instanceof SafeValue.SafeResourceUrl safe
                    ? safe.value()
                    : throwUnsafe("unsafe value used in a resource URL context");
        };
    }

    private static Object unwrapOrFail(Object value, SecurityContext expected) {
        if (value instanceof SafeValue safe) {
            throw new IllegalArgumentException("Required a safe " + expected + ", got a " + safe.getClass().getSimpleName());
        }
        return value;
    }

    private static String throwUnsafe(String message) {
        throw new IllegalArgumentException(message + " (see https://angular.dev/best-practices/security#xss)");
    }

    public SafeValue.SafeHtml bypassSecurityTrustHtml(String value) {
        return new SafeValue.SafeHtml(value);
    }

    public SafeValue.SafeStyle bypassSecurityTrustStyle(String value) {
        return new SafeValue.SafeStyle(value);
    }

    public SafeValue.SafeScript bypassSecurityTrustScript(String value) {
        return new SafeValue.SafeScript(value);
    }

    public SafeValue.SafeUrl bypassSecurityTrustUrl(String value) {
        return new SafeValue.SafeUrl(value);
    }

    public SafeValue.SafeResourceUrl bypassSecurityTrustResourceUrl(String value) {
        return new SafeValue.SafeResourceUrl(value);
    }
}
