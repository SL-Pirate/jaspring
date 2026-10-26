package dev.isira.jaspring.template.parse;

import dev.isira.jaspring.template.ast.ParsedTemplate;

public interface TemplateParser {

    /// Parses `template`; `url` only appears in error messages.
    /// @throws TemplateCompileException when Angular reports errors and `options.tolerateErrors()` is false
    ParsedTemplate parse(String template, String url, ParseOptions options);
}
