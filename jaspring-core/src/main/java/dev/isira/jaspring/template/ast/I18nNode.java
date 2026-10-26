package dev.isira.jaspring.template.ast;

import java.util.List;
import java.util.Map;

/// Angular's i18n message tree (`i18n/i18n_ast.ts`), used for the case structure of ICU expansions.
public sealed interface I18nNode {

    record Text(String value) implements I18nNode {}

    record Container(List<I18nNode> children) implements I18nNode {}

    /// `cases` keys are `=0`, `one`, `other`, ... ; `expressionPlaceholder` names the entry in `TemplateNode.Icu.vars`.
    record Icu(String expression, String type, Map<String, I18nNode> cases, String expressionPlaceholder)
            implements I18nNode {}

    /// `name` keys into `TemplateNode.Icu.placeholders`.
    record Placeholder(String value, String name) implements I18nNode {}

    record IcuPlaceholder(Icu value, String name) implements I18nNode {}

    record TagPlaceholder(String tag, Map<String, String> attrs, String startName, String closeName,
            List<I18nNode> children, boolean isVoid) implements I18nNode {}

    record BlockPlaceholder(String name, List<String> parameters, String startName, String closeName,
            List<I18nNode> children) implements I18nNode {}
}
