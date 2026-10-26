package dev.isira.jaspring.signals;

import java.util.Objects;

/// Decides whether a new value counts as a change. Unlike Angular, the default is `Objects.equals`
/// because Java records and collections are value types; use `identity()` for Angular's `Object.is`.
@FunctionalInterface
public interface Equality<T> {

    boolean equal(T a, T b);

    static <T> Equality<T> defaultEquality() {
        return Objects::equals;
    }

    static <T> Equality<T> identity() {
        return (a, b) -> a == b;
    }
}
