package dev.isira.jaspring.live;

import java.util.Map;

/// What to render: the root component and its initial inputs.
public record PageSpec(Class<?> rootComponent, Map<String, Object> inputs) {

    public static PageSpec of(Class<?> rootComponent) {
        return new PageSpec(rootComponent, Map.of());
    }

    public PageSpec input(String name, Object value) {
        final var copy = new java.util.LinkedHashMap<>(inputs);
        copy.put(name, value);
        return new PageSpec(rootComponent, java.util.Collections.unmodifiableMap(copy));
    }
}
