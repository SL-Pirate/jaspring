package dev.isira.jaspring.template.security;

/// A value the application vouches for; the renderer emits it without sanitizing. Created through `DomSanitizer`.
public sealed interface SafeValue {

    String value();

    record SafeHtml(String value) implements SafeValue {}

    record SafeStyle(String value) implements SafeValue {}

    record SafeScript(String value) implements SafeValue {}

    record SafeUrl(String value) implements SafeValue {}

    record SafeResourceUrl(String value) implements SafeValue {}
}
