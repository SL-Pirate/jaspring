package dev.isira.jaspring.cd.lifecycle;

import java.util.LinkedHashMap;
import java.util.Map;

/// The inputs that changed in one check, keyed by public input name.
public final class SimpleChanges {

    private final Map<String, SimpleChange> changes = new LinkedHashMap<>();

    public void put(String name, SimpleChange change) {
        changes.put(name, change);
    }

    public SimpleChange get(String name) {
        return changes.get(name);
    }

    public boolean has(String name) {
        return changes.containsKey(name);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public Map<String, SimpleChange> asMap() {
        return Map.copyOf(changes);
    }
}
