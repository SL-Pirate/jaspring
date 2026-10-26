package dev.isira.jaspring.template.render;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.template.eval.Js;

/// Merges the class and style sources of one element: the static attribute, `[class]`/`[style]`, `[class.x]`/`[style.x.unit]`
/// and `ngClass`/`ngStyle`, in Angular's precedence (later and more specific bindings win).
final class StyleBindings {

    private final Set<String> classes = new LinkedHashSet<>();
    private final Map<String, String> styles = new LinkedHashMap<>();

    void staticClass(String value) {
        for (final var name : value.trim().split("\\s+")) {
            if (!name.isEmpty()) classes.add(name);
        }
    }

    void staticStyle(String value) {
        for (final var declaration : value.split(";")) {
            final var colon = declaration.indexOf(':');
            if (colon < 0) continue;
            styles.put(declaration.substring(0, colon).trim(), declaration.substring(colon + 1).trim());
        }
    }

    /// `[class]`, `[ngClass]`: a string of names, an iterable of names, or a map of name to truthy.
    void classValue(Object value) {
        switch (value) {
            case null -> { }
            case CharSequence s -> staticClass(s.toString());
            case Map<?, ?> map -> map.forEach((name, on) -> toggle(Js.toJsString(name), Js.truthy(on)));
            case Collection<?> names -> names.forEach(name -> staticClass(Js.toJsString(name)));
            default -> {
                if (!Js.isNullish(value)) staticClass(Js.toJsString(value));
            }
        }
    }

    void toggle(String spaceSeparatedNames, boolean on) {
        for (final var name : spaceSeparatedNames.trim().split("\\s+")) {
            if (name.isEmpty()) continue;
            if (on) classes.add(name); else classes.remove(name);
        }
    }

    /// `[style]`, `[ngStyle]`: a CSS text or a map of property to value (`'width.px'` keys carry a unit).
    void styleValue(Object value) {
        switch (value) {
            case null -> { }
            case CharSequence s -> staticStyle(s.toString());
            case Map<?, ?> map -> map.forEach((key, v) -> {
                final var name = Js.toJsString(key);
                final var dot = name.indexOf('.');
                style(dot < 0 ? name : name.substring(0, dot), dot < 0 ? null : name.substring(dot + 1), v);
            });
            default -> { }
        }
    }

    void style(String property, String unit, Object value) {
        final var name = camelToKebab(property);
        if (Js.isNullish(value) || "".equals(value) || Boolean.FALSE.equals(value)) {
            styles.remove(name);
            return;
        }
        final var text = Js.toJsString(value);
        styles.put(name, unit != null && value instanceof Number ? text + unit : text);
    }

    String classAttribute() {
        return classes.isEmpty() ? null : String.join(" ", classes);
    }

    String styleAttribute() {
        if (styles.isEmpty()) return null;
        final var sb = new StringBuilder();
        styles.forEach((name, value) -> {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(name).append(": ").append(value);
        });
        return sb.toString();
    }

    private static String camelToKebab(String property) {
        if (property.startsWith("--") || property.contains("-")) return property;
        final var sb = new StringBuilder();
        for (var i = 0; i < property.length(); i++) {
            final var c = property.charAt(i);
            if (Character.isUpperCase(c)) sb.append('-').append(Character.toLowerCase(c)); else sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
