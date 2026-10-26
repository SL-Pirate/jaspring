package dev.isira.jaspring.ide;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;

import java.util.Collection;
import java.util.Objects;

public final class JaspringLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    public String getName() { return "Jaspring templates and styles"; }

    @Override
    protected void collectNavigationMarkers(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiClass type)
                || element != type.getNameIdentifier()) return;
        final var targets = new JaspringRelatedProvider().getItems(element).stream()
                .map(item -> item.getElement()).filter(Objects::nonNull).toList();
        if (targets.isEmpty()) return;
        result.add(NavigationGutterIconBuilder.create(AllIcons.FileTypes.Html).setTargets(targets)
                .setTooltipText("Navigate to Jaspring template or stylesheet").createLineMarkerInfo(element));
    }
}
