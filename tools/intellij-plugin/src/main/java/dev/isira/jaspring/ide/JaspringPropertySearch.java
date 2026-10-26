package dev.isira.jaspring.ide;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;

import java.beans.Introspector;

/// JavaBean properties appear in templates without the getter prefix.
public final class JaspringPropertySearch extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
    public JaspringPropertySearch() { super(true); }

    @Override
    public void processQuery(MethodReferencesSearch.SearchParameters parameters, Processor<? super PsiReference> consumer) {
        final var method = parameters.getMethod();
        if (!method.getParameterList().isEmpty()) return;
        final var name = method.getName();
        final var prefix = name.startsWith("get") ? 3 : name.startsWith("is") ? 2 : 0;
        if (prefix == 0 || name.length() <= prefix) return;
        parameters.getOptimizer().searchWord(Introspector.decapitalize(name.substring(prefix)),
                parameters.getEffectiveSearchScope(), UsageSearchContext.ANY, true, method);
    }
}
