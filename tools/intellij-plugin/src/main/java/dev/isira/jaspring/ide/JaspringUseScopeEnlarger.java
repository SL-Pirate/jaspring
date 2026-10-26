package dev.isira.jaspring.ide;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;

/// Private Java members are visible in their component templates, so Java's lexical use scope is too narrow.
public final class JaspringUseScopeEnlarger extends UseScopeEnlarger {
    @Override
    public SearchScope getAdditionalUseScope(PsiElement element) {
        if (!(element instanceof PsiMember member) || member.getContainingClass() == null
                || DumbService.isDumb(element.getProject())) return null;
        final var files = new ArrayList<PsiElement>();
        for (final var declaration : JaspringProjectModel.declarations(element.getProject())) {
            if (!(declaration.type().equals(member.getContainingClass())
                    || declaration.type().isInheritor(member.getContainingClass(), true))) continue;
            if (member instanceof PsiField field && TemplateBindings.bindings(declaration.type()).stream()
                    .anyMatch(binding -> binding.field().equals(field))) {
                return GlobalSearchScope.projectScope(element.getProject());
            }
            if (!declaration.isComponent()) continue;
            final var resource = JaspringProjectModel.resource(declaration.type(),
                    JaspringProjectModel.stringValue(declaration.annotation(), "templateUrl"));
            if (resource != null) {
                final var file = PsiManager.getInstance(element.getProject()).findFile(resource);
                if (file != null) files.add(file);
            }
        }
        return files.isEmpty() ? null : new LocalSearchScope(files.toArray(PsiElement[]::new));
    }
}
