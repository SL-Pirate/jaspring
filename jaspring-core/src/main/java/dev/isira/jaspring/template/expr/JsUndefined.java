package dev.isira.jaspring.template.expr;

/// JavaScript `undefined`, kept distinct from `null` because Angular expressions can tell them apart (`== null` vs `=== null`).
public enum JsUndefined {
    INSTANCE;

    @Override
    public String toString() {
        return "undefined";
    }
}
