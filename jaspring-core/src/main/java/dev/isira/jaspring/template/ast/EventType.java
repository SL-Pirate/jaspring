package dev.isira.jaspring.template.ast;

/// Mirrors Angular's `ParsedEventType` ordinals.
public enum EventType {
    REGULAR, LEGACY_ANIMATION, TWO_WAY, ANIMATION;

    public static EventType fromOrdinal(int ordinal) {
        return values()[ordinal];
    }
}
