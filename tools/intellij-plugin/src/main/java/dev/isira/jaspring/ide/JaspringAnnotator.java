package dev.isira.jaspring.ide;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public final class JaspringAnnotator implements Annotator {
    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        if (!(element instanceof PsiFile file) || JaspringProjectModel.owners(file).isEmpty()) return;
        final var syntax = TemplateSyntax.of(file);
        for (final var block : syntax.blocks()) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(block.range())
                    .textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create();
        }
        for (final var expression : syntax.expressions()) {
            for (final var token : expression.tokens()) {
                if (token.range().isEmpty()) continue;
                final var style = token.identifier() ? DefaultLanguageHighlighterColors.INSTANCE_FIELD
                        : token.text().startsWith("'") || token.text().startsWith("\"") ? DefaultLanguageHighlighterColors.STRING
                        : Character.isDigit(token.text().charAt(0)) ? DefaultLanguageHighlighterColors.NUMBER
                        : Character.isJavaIdentifierStart(token.text().charAt(0)) ? DefaultLanguageHighlighterColors.KEYWORD : null;
                if (style != null) holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(token.range()).textAttributes(style).create();
            }
        }
    }
}
