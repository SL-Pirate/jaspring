package dev.isira.jaspring.template.ast;

/// Mirrors Angular's `BindingType` ordinals.
public enum BindingType {
    PROPERTY, ATTRIBUTE, CLASS, STYLE, LEGACY_ANIMATION, TWO_WAY, ANIMATION;

    public static BindingType fromOrdinal(int ordinal) {
        return values()[ordinal];
    }
}
