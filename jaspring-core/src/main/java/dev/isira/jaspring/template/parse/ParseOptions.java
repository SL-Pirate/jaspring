package dev.isira.jaspring.template.parse;

/// The subset of Angular's `ParseTemplateOptions` a component can influence.
public record ParseOptions(boolean preserveWhitespaces) {

    public static ParseOptions defaults() {
        return new ParseOptions(false);
    }
}
