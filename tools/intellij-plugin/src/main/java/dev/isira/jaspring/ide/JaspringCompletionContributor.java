package dev.isira.jaspring.ide;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.List;

public final class JaspringCompletionContributor extends CompletionContributor {
    private static final List<String> PIPES = List.of("async", "uppercase", "lowercase", "titlecase", "json", "slice", "keyvalue",
            "date", "number", "percent", "currency", "i18nPlural", "i18nSelect");

    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        final var file = parameters.getOriginalFile();
        if (JaspringProjectModel.owners(file).isEmpty()) return;
        final var offset = parameters.getOffset();
        final var source = file.getText();
        final var expression = TemplateSyntax.of(file).expressionAt(offset);
        if (expression != null) {
            final var prefix = identifierPrefix(source, offset);
            final var matched = result.withPrefixMatcher(prefix);
            final var before = source.substring(expression.range().getStartOffset(), Math.max(expression.range().getStartOffset(), offset - prefix.length())).stripTrailing();
            if (before.endsWith("|") && !before.endsWith("||")) {
                for (final var pipe : PIPES) matched.addElement(LookupElementBuilder.create(pipe).withTypeText("built-in pipe"));
                for (final var declaration : JaspringProjectModel.visibleDeclarations(file)) {
                    if (!declaration.pipeName().isEmpty()) matched.addElement(LookupElementBuilder.create(declaration.type(), declaration.pipeName())
                            .withTypeText(declaration.type().getName()));
                }
            } else {
                for (final var symbol : JavaTemplateSymbols.completion(file, expression, offset)) {
                    var lookup = symbol.target() == null ? LookupElementBuilder.create(symbol.name())
                            : LookupElementBuilder.create(symbol.target(), symbol.name());
                    if (symbol.type() != null) lookup = lookup.withTypeText(symbol.type().getPresentableText());
                    if (symbol.method()) lookup = lookup.withTailText("(…)", true);
                    matched.addElement(lookup);
                }
            }
            result.stopHere();
            return;
        }
        if (offset > 0 && source.substring(0, offset).matches("(?s).*@[a-zA-Z]*$")) {
            for (final var block : List.of("if", "else", "for", "empty", "switch", "case", "default", "defer", "placeholder", "loading", "error", "let")) {
                result.withPrefixMatcher(identifierPrefix(source, offset)).addElement(LookupElementBuilder.create(block).withTypeText("Jaspring block"));
            }
            return;
        }
        final var position = parameters.getPosition();
        final var attribute = PsiTreeUtil.getParentOfType(position, XmlAttribute.class, false);
        final var tag = PsiTreeUtil.getParentOfType(position, XmlTag.class, false);
        if (tag == null) return;
        final var open = source.lastIndexOf('<', offset - 1);
        final var close = source.lastIndexOf('>', offset - 1);
        if (open < close) return;
        if (attribute == null && source.substring(open + 1, offset).matches("/?[\\w-]*")) {
            for (final var declaration : JaspringProjectModel.visibleDeclarations(file)) {
                for (final var selector : declaration.selector().split(",")) {
                    if (selector.trim().matches("[a-zA-Z][\\w-]*")) {
                        result.addElement(LookupElementBuilder.create(declaration.type(), selector.trim()).withTypeText(declaration.type().getName()));
                    }
                }
            }
        } else {
            for (final var binding : TemplateBindings.forTag(tag)) {
                final var name = switch (binding.kind()) {
                    case EVENT -> "(" + binding.name() + ")";
                    case TWO_WAY -> "[(" + binding.name() + ")]";
                    default -> "[" + binding.name() + "]";
                };
                result.addElement(LookupElementBuilder.create(binding.field(), name).withTypeText(binding.field().getType().getPresentableText()));
            }
            for (final var builtin : List.of("[ngClass]", "[ngStyle]", "[(ngModel)]", "*ngIf", "*ngFor", "[ngSwitch]", "*ngSwitchCase", "*ngSwitchDefault")) {
                result.addElement(LookupElementBuilder.create(builtin));
            }
        }
    }

    private static String identifierPrefix(String source, int offset) {
        var start = Math.min(offset, source.length());
        while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) start--;
        return source.substring(start, Math.min(offset, source.length()));
    }
}
