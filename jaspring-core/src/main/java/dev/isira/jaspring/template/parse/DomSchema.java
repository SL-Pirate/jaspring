package dev.isira.jaspring.template.parse;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Angular's `DomElementSchemaRegistry`, dumped once from the JS bundle: which properties each element has and their type.
/// Inherited properties are already flattened into each element entry.
public final class DomSchema {

    private final Map<String, Map<String, String>> elements;
    private final Map<String, Set<String>> events;
    private final Map<String, String> attributeToProperty;

    private DomSchema(Map<String, Map<String, String>> elements, Map<String, Set<String>> events, Map<String, String> attributeToProperty) {
        this.elements = elements;
        this.events = events;
        this.attributeToProperty = attributeToProperty;
    }

    public static DomSchema load(AngularCompilerRuntime runtime) {
        return runtime.domSchema(value -> {
            final var elements = new HashMap<String, Map<String, String>>();
            final var elementsValue = value.getMember("elements");
            for (final var tag : elementsValue.getMemberKeys()) {
                final var props = new HashMap<String, String>();
                final var propsValue = elementsValue.getMember(tag);
                for (final var prop : propsValue.getMemberKeys()) {
                    props.put(prop, propsValue.getMember(prop).asString());
                }
                elements.put(tag, Map.copyOf(props));
            }
            final var events = new HashMap<String, Set<String>>();
            final var eventsValue = value.getMember("events");
            for (final var tag : eventsValue.getMemberKeys()) {
                final var names = eventsValue.getMember(tag);
                final var set = new java.util.HashSet<String>();
                for (var i = 0L; i < names.getArraySize(); i++) {
                    set.add(names.getArrayElement(i).asString());
                }
                events.put(tag, Set.copyOf(set));
            }
            final var attributeToProperty = new HashMap<String, String>();
            final var mapping = value.getMember("attrToProp");
            for (final var attr : mapping.getMemberKeys()) {
                attributeToProperty.put(attr, mapping.getMember(attr).asString());
            }
            return new DomSchema(Map.copyOf(elements), Map.copyOf(events), Map.copyOf(attributeToProperty));
        });
    }

    public boolean hasElement(String tagName) {
        final var tag = tagName.toLowerCase(Locale.ROOT);
        return elements.containsKey(tag) || tag.contains("-");
    }

    /// `boolean`, `number`, `string`, `object`, or `null` when the element has no such property.
    public String propertyType(String tagName, String property) {
        var props = elements.get(tagName.toLowerCase(Locale.ROOT));
        if (props == null) props = elements.get("unknown");
        return props == null ? null : props.get(mappedPropertyName(property));
    }

    public boolean hasProperty(String tagName, String property) {
        return propertyType(tagName, property) != null;
    }

    public String mappedPropertyName(String attributeName) {
        return attributeToProperty.getOrDefault(attributeName, attributeName);
    }

    public List<String> knownElements() {
        return elements.keySet().stream().filter(name -> !name.startsWith("[") && !name.equals("unknown")).sorted().toList();
    }
}
