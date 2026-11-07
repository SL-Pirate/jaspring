package dev.isira.jaspring.typescript;

import java.util.List;
import java.util.Optional;

/// Emitted files and diagnostics. Outputs are present even when there are errors (TypeScript emits regardless),
/// so callers decide with [#hasErrors()] whether to use them.
public record CompileResult(List<Output> outputs, List<Diagnostic> diagnostics) {

    public CompileResult {
        outputs = List.copyOf(outputs);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    public Optional<Output> output(String path) {
        return outputs.stream().filter(output -> output.path().equals(path)).findFirst();
    }

    /// `path` is relative to the output root, with `/` separators (`hooks/sparkline.js`, `hooks/sparkline.js.map`).
    public record Output(String path, String text) {}

    /// `file` is the source path, or null for options and global diagnostics; `line` and `column` are 1-based and
    /// 0 when unknown. `code` is TypeScript's number (2322 for `TS2322`).
    public record Diagnostic(String file, int line, int column, int code, Category category, String message) {

        public enum Category { ERROR, WARNING, SUGGESTION, MESSAGE }

        public boolean isError() {
            return category == Category.ERROR;
        }

        /// tsc's own layout, which editors and people recognise: `hooks/sparkline.ts(12,5): error TS2322: ...`.
        public String format() {
            return format("");
        }

        /// `pathPrefix` is put before the file name, so a build can print `src/main/typescript/hooks/sparkline.ts`.
        public String format(String pathPrefix) {
            final var level = category.name().toLowerCase(java.util.Locale.ROOT) + " TS" + code + ": " + message;
            if (file == null) return level;
            final var location = line > 0 ? "(" + line + "," + column + ")" : "";
            return pathPrefix + file + location + ": " + level;
        }
    }
}
