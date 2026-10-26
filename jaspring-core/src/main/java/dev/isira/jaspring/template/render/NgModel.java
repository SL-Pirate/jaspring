package dev.isira.jaspring.template.render;

import java.util.Collection;
import java.util.Map;

import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.Js;

/// Server-side `ngModel`: reflects the bound value into the markup the browser would show
/// (`value`, `checked`, `selected`, textarea content). Changes come back through `ngModelChange`.
final class NgModel {

    private NgModel() {}

    /// @return the text content to render for a `<textarea>`, otherwise the content passed in
    static String reflect(String tagName, Map<String, String> attributes, Object value, String textContent) {
        switch (tagName) {
            case "textarea" -> {
                return Js.isNullish(value) ? "" : Js.toJsString(value);
            }
            case "select" -> {
                return textContent;
            }
            case "input" -> {
                final var type = attributes.getOrDefault("type", "text").toLowerCase(java.util.Locale.ROOT);
                if (type.equals("checkbox")) {
                    if (Js.truthy(value)) attributes.put("checked", ""); else attributes.remove("checked");
                } else if (type.equals("radio")) {
                    if (Js.strictEquals(attributes.get("value"), Js.isNullish(value) ? null : Js.toJsString(value))) attributes.put("checked", "");
                    else attributes.remove("checked");
                } else if (Js.isNullish(value)) {
                    attributes.remove("value");
                } else {
                    attributes.put("value", Js.toJsString(value));
                }
                return textContent;
            }
            default -> {
                if (!Js.isNullish(value)) attributes.put("value", Js.toJsString(value));
                return textContent;
            }
        }
    }

    /// An `<option>` is selected when its value (the `value` attribute, else its text) equals the select's model.
    static void markSelected(Map<String, String> attributes, Object model, boolean multiple, String textContent, TemplateNode.Element option) {
        final var optionValue = attributes.containsKey("value") ? attributes.get("value") : staticText(option);
        final var selected = multiple && model instanceof Collection<?> values
                ? values.stream().anyMatch(v -> Js.strictEquals(Js.toJsString(v), optionValue))
                : !Js.isNullish(model) && Js.strictEquals(Js.toJsString(model), optionValue);
        if (selected) attributes.put("selected", ""); else attributes.remove("selected");
    }

    private static String staticText(TemplateNode.Element option) {
        final var sb = new StringBuilder();
        for (final var child : option.children()) {
            if (child instanceof TemplateNode.Text text) sb.append(text.value());
        }
        return sb.toString().trim();
    }
}
