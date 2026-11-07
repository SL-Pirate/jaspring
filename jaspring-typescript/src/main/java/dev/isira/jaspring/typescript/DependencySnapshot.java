package dev.isira.jaspring.typescript;

import java.util.List;
import java.util.Map;

/// Read-only package metadata and declarations mounted beside `/src` in the compiler's virtual filesystem.
/// Paths are relative to its root. Only globalTypes are additional program roots; other files are loaded on demand.
public record DependencySnapshot(Map<String, String> files, List<String> globalTypes, List<String> allowedImports) {
    public static final DependencySnapshot EMPTY = new DependencySnapshot(Map.of(), List.of(), List.of());

    public DependencySnapshot {
        files = Map.copyOf(files);
        globalTypes = List.copyOf(globalTypes);
        allowedImports = List.copyOf(allowedImports);
        for (final var path : files.keySet()) {
            if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || java.util.Arrays.stream(path.split("/", -1))
                    .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
                throw new IllegalArgumentException("Invalid dependency path: " + path);
            }
        }
        for (final var path : globalTypes) if (!files.containsKey(path)) throw new IllegalArgumentException("Missing global declaration: " + path);
    }
}
