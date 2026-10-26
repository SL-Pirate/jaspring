package dev.isira.jaspring.template.render;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.template.parse.DomSchema;

/// How a `[property]` binding becomes markup on the server. In the browser Angular sets DOM properties;
/// here the property is reflected to the attribute the browser would parse it from.
public final class PropertyReflection {

    public enum Kind { ATTRIBUTE, BOOLEAN_ATTRIBUTE, INNER_HTML, TEXT_CONTENT, SKIP }

    public record Reflection(Kind kind, String attribute) {}

    private static final Map<String, String> ATTRIBUTE_NAMES = Map.ofEntries(
            Map.entry("className", "class"), Map.entry("htmlFor", "for"), Map.entry("readOnly", "readonly"),
            Map.entry("tabIndex", "tabindex"), Map.entry("maxLength", "maxlength"), Map.entry("minLength", "minlength"),
            Map.entry("colSpan", "colspan"), Map.entry("rowSpan", "rowspan"), Map.entry("formAction", "formaction"),
            Map.entry("httpEquiv", "http-equiv"), Map.entry("acceptCharset", "accept-charset"), Map.entry("dateTime", "datetime"),
            Map.entry("noValidate", "novalidate"), Map.entry("isMap", "ismap"), Map.entry("useMap", "usemap"),
            Map.entry("srcSet", "srcset"), Map.entry("crossOrigin", "crossorigin"), Map.entry("autoComplete", "autocomplete"),
            Map.entry("contentEditable", "contenteditable"), Map.entry("enterKeyHint", "enterkeyhint"), Map.entry("inputMode", "inputmode"),
            Map.entry("accessKey", "accesskey"), Map.entry("defaultValue", "value"), Map.entry("defaultChecked", "checked"),
            Map.entry("defaultSelected", "selected"), Map.entry("encType", "enctype"), Map.entry("autoFocus", "autofocus"),
            Map.entry("noWrap", "nowrap"), Map.entry("hrefLang", "hreflang"), Map.entry("referrerPolicy", "referrerpolicy"),
            Map.entry("frameBorder", "frameborder"), Map.entry("allowFullscreen", "allowfullscreen"), Map.entry("fetchPriority", "fetchpriority"));

    private static final Set<String> NOT_REFLECTED = Set.of(
            "scrollTop", "scrollLeft", "scrollWidth", "scrollHeight", "currentTime", "volume", "playbackRate",
            "defaultPlaybackRate", "indeterminate", "selectionStart", "selectionEnd", "selectionDirection", "valueAsDate",
            "valueAsNumber", "outerHTML", "outerText", "nodeValue", "textContent_", "offsetWidth", "offsetHeight");

    private final DomSchema schema;

    public PropertyReflection(DomSchema schema) {
        this.schema = schema;
    }

    public Reflection reflect(String tagName, String property) {
        final var name = schema.mappedPropertyName(property);
        switch (name) {
            case "innerHTML" -> {
                return new Reflection(Kind.INNER_HTML, null);
            }
            case "textContent", "innerText" -> {
                return new Reflection(Kind.TEXT_CONTENT, null);
            }
            default -> { }
        }
        if (NOT_REFLECTED.contains(name)) return new Reflection(Kind.SKIP, null);
        final var type = schema.propertyType(tagName, name);
        if ("boolean".equals(type)) return new Reflection(Kind.BOOLEAN_ATTRIBUTE, attributeName(name));
        if ("object".equals(type)) return new Reflection(Kind.SKIP, null);
        return new Reflection(Kind.ATTRIBUTE, attributeName(name));
    }

    /// The attribute a property reflects to. Unknown names (custom elements) are kept as written.
    public static String attributeName(String property) {
        final var mapped = ATTRIBUTE_NAMES.get(property);
        if (mapped != null) return mapped;
        if (property.startsWith("aria") && property.length() > 4 && Character.isUpperCase(property.charAt(4))) {
            return "aria-" + property.substring(4).toLowerCase(Locale.ROOT);
        }
        return property.chars().anyMatch(Character::isUpperCase) && !property.contains("-") ? property.toLowerCase(Locale.ROOT) : property;
    }
}
