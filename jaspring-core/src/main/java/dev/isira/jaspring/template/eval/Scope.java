package dev.isira.jaspring.template.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/// Template locals (`@let`, `@for` variables, `#refs`, `$event`) as an immutable chain of frames.
/// Lookup walks from the innermost frame outwards; the component instance is consulted by the evaluator afterwards.
public final class Scope {

    static final Object MISSING = new Object();

    private static final Scope ROOT = new Scope(null, Map.of());

    private final Scope parent;
    private final Map<String, Object> locals;

    private Scope(Scope parent, Map<String, Object> locals) {
        this.parent = parent;
        this.locals = locals;
    }

    public static Scope root() {
        return ROOT;
    }

    public Scope child(Map<String, Object> locals) {
        return new Scope(this, new LinkedHashMap<>(locals));
    }

    public Scope with(String name, Object value) {
        final var map = new LinkedHashMap<String, Object>(1);
        map.put(name, value);
        return new Scope(this, map);
    }

    public boolean has(String name) {
        return find(name) != MISSING;
    }

    /// @return the local's value (possibly `null`), or `MISSING` when no frame declares it
    Object find(String name) {
        for (var scope = this; scope != null; scope = scope.parent) {
            if (scope.locals.containsKey(name)) {
                return scope.locals.get(name);
            }
        }
        return MISSING;
    }

    /// Template references and `@let` values are filled in after the frame exists (an element can be referenced before it renders).
    public void define(String name, Object value) {
        locals.put(name, value);
    }
}
