package dev.isira.jaspring.pipe;

/// A template pipe: `{{ value | name:arg1:arg2 }}` calls `transform(value, arg1, arg2)`.
@FunctionalInterface
public interface PipeTransform {

    Object transform(Object value, Object... args);
}
