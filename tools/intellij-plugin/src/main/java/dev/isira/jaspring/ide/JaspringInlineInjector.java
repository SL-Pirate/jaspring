package dev.isira.jaspring.ide;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

public final class JaspringInlineInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(MultiHostRegistrar registrar, PsiElement context) {
        if (!(context instanceof PsiLiteralExpression literal) || !(literal.getValue() instanceof String)
                || !(literal instanceof PsiLanguageInjectionHost host)) return;
        final var pair = PsiTreeUtil.getParentOfType(context, PsiNameValuePair.class);
        final var annotation = PsiTreeUtil.getParentOfType(context, PsiAnnotation.class);
        if (pair == null || annotation == null || !annotation.hasQualifiedName(JaspringProjectModel.COMPONENT)) return;
        if (pair.getAttributeName().equals("template") && pair.getValue() != literal) return;
        final var language = switch (pair.getAttributeName()) {
            case "template" -> JaspringHtmlLanguage.INSTANCE;
            case "styles" -> Language.findLanguageByID("CSS");
            default -> null;
        };
        if (language == null) return;
        final var range = host.createLiteralTextEscaper().getRelevantTextRange();
        registrar.startInjecting(language).addPlace(null, null, host, range).doneInjecting();
    }

    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(PsiLiteralExpression.class);
    }
}
