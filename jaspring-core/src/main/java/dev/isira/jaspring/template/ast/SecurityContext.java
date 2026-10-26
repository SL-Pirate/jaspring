package dev.isira.jaspring.template.ast;

/// Mirrors Angular's `SecurityContext` ordinals. The parser stamps it on every bound attribute.
public enum SecurityContext {
    NONE, HTML, STYLE, SCRIPT, URL, RESOURCE_URL, ATTRIBUTE_NO_BINDING;

    public static SecurityContext fromOrdinal(int ordinal) {
        return values()[ordinal];
    }
}
