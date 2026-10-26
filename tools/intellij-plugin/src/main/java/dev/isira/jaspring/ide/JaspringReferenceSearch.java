package dev.isira.jaspring.ide;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

/// Binding aliases and model outputs have different indexed words from their Java fields.
public final class JaspringReferenceSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
    public JaspringReferenceSearch() { super(true); }

    @Override
    public void processQuery(ReferencesSearch.SearchParameters parameters, Processor<? super PsiReference> consumer) {
        if (!(parameters.getElementToSearch() instanceof PsiField field) || field.getContainingClass() == null) return;
        for (final var binding : TemplateBindings.bindings(field.getContainingClass())) {
            if (!binding.field().equals(field) || binding.name().equals(field.getName())) continue;
            parameters.getOptimizer().searchWord(binding.name(), parameters.getEffectiveSearchScope(), UsageSearchContext.ANY, true, field);
        }
    }
}
