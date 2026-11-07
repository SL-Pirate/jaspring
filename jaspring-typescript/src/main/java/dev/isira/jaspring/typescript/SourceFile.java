package dev.isira.jaspring.typescript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/// One `.ts` or `.d.ts` file. `path` is relative to the source root with `/` separators and no `.` or `..`
/// segments; it becomes the output path with `.js`, so it must be a safe URL path too.
public record SourceFile(String path, String text) {

    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(text, "text");
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.endsWith("/")) {
            throw new IllegalArgumentException("Source path must be relative with '/' separators: '" + path + "'");
        }
        for (final var segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Source path must not contain empty, '.' or '..' segments: '" + path + "'");
            }
        }
        if (!path.endsWith(".ts")) {
            throw new IllegalArgumentException("Not a TypeScript file (expected .ts or .d.ts): '" + path + "'");
        }
    }

    /// Reads `file` as the source named by its path relative to `root`.
    public static SourceFile read(Path root, Path file) throws IOException {
        final var relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
        return new SourceFile(relative, Files.readString(file, StandardCharsets.UTF_8));
    }

    public boolean declaration() {
        return path.endsWith(".d.ts");
    }

    /// The `.js` the compiler emits for this file; declaration files emit nothing.
    public String outputPath() {
        return declaration() ? null : path.substring(0, path.length() - ".ts".length()) + ".js";
    }
}
