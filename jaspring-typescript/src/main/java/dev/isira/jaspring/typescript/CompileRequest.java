package dev.isira.jaspring.typescript;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// The files of one program. The compiler adds `jaspring.d.ts` and the standard libs itself.
public record CompileRequest(List<SourceFile> files, CompileOptions options) {

    public CompileRequest {
        files = List.copyOf(files);
        Objects.requireNonNull(options, "options");
        final var seen = new HashSet<String>();
        for (final var file : files) {
            if (!seen.add(file.path())) throw new IllegalArgumentException("Duplicate source path: '" + file.path() + "'");
        }
    }

    public static CompileRequest of(List<SourceFile> files) {
        return new CompileRequest(files, CompileOptions.defaults());
    }
}
