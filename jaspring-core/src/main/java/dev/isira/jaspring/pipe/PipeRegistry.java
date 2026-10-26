package dev.isira.jaspring.pipe;

import java.util.Map;

/// Resolves pipe names used in templates. Unknown names are reported at compile time; at evaluation time they are a bug.
@FunctionalInterface
public interface PipeRegistry {

    PipeRegistry EMPTY = name -> null;

    /// @return the pipe, or `null` when no pipe has that name
    PipeTransform get(String name);

    static PipeRegistry of(Map<String, PipeTransform> pipes) {
        final var copy = Map.copyOf(pipes);
        return copy::get;
    }
}
