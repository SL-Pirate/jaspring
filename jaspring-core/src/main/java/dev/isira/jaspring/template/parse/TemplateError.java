package dev.isira.jaspring.template.parse;

/// One parse or compile error, with Angular's own wording in `text` (`message ("...[ERROR ->]...") url@line:col`).
public record TemplateError(String message, String text, String url, int line, int col, int start, int end, Level level) {

    public enum Level {
        WARNING, ERROR;

        static Level fromOrdinal(int ordinal) {
            return values()[ordinal];
        }
    }
}
