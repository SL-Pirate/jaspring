// Copyright 2000-2020 JetBrains s.r.o.
// Licensed under the Apache License, Version 2.0 (see licenses/Apache-2.0.txt).
// Adapted for Jaspring: Java port of the binding-name classification in Angular2AttributeNameParser;
// removed Angular PSI, animation validation, DOM schema and localization dependencies.
package dev.isira.jaspring.ide;

record BindingName(String name, int offset, Kind kind) {
    enum Kind { REGULAR, PROPERTY, EVENT, TWO_WAY, STRUCTURAL, REFERENCE, LOCAL }

    static BindingName parse(String raw) {
        final var dataOffset = raw.regionMatches(true, 0, "data-", 0, 5) ? 5 : 0;
        final var name = raw.substring(dataOffset);
        if (name.startsWith("bindon-")) return new BindingName(name.substring(7), dataOffset + 7, Kind.TWO_WAY);
        if (name.startsWith("[(") && name.endsWith(")]")) {
            return new BindingName(name.substring(2, name.length() - 2), dataOffset + 2, Kind.TWO_WAY);
        }
        if (name.startsWith("bind-")) return new BindingName(name.substring(5), dataOffset + 5, Kind.PROPERTY);
        if (name.startsWith("[") && name.endsWith("]")) {
            return new BindingName(name.substring(1, name.length() - 1), dataOffset + 1, Kind.PROPERTY);
        }
        if (name.startsWith("on-")) return new BindingName(name.substring(3), dataOffset + 3, Kind.EVENT);
        if (name.startsWith("(") && name.endsWith(")")) {
            return new BindingName(name.substring(1, name.length() - 1), dataOffset + 1, Kind.EVENT);
        }
        if (name.startsWith("*")) return new BindingName(name.substring(1), dataOffset + 1, Kind.STRUCTURAL);
        if (name.startsWith("let-")) return new BindingName(name.substring(4), dataOffset + 4, Kind.LOCAL);
        if (name.startsWith("#")) return new BindingName(name.substring(1), dataOffset + 1, Kind.REFERENCE);
        if (name.startsWith("ref-")) return new BindingName(name.substring(4), dataOffset + 4, Kind.REFERENCE);
        return new BindingName(name, dataOffset, Kind.REGULAR);
    }

    boolean hasExpression() {
        return kind == Kind.PROPERTY || kind == Kind.EVENT || kind == Kind.TWO_WAY || kind == Kind.STRUCTURAL;
    }
}
