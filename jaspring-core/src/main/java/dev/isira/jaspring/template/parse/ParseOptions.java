package dev.isira.jaspring.template.parse;

/// The subset of Angular's `ParseTemplateOptions` a component can influence, plus `tolerateErrors`
/// for tooling that wants the partial tree instead of an exception.
public record ParseOptions(boolean preserveWhitespaces, boolean tolerateErrors) {

    public static ParseOptions defaults() {
        return new ParseOptions(false, false);
    }
}
