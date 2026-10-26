package dev.isira.jaspring.ide;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;

public final class JaspringRelatedProvider extends GotoRelatedProvider {
    @Override
    public List<? extends GotoRelatedItem> getItems(PsiElement context) {
        final var result = new ArrayList<GotoRelatedItem>();
        final var file = context.getContainingFile();
        if (file == null) return List.of();
        final var type = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (type != null) {
            final var annotation = type.getAnnotation(JaspringProjectModel.COMPONENT);
            if (annotation == null) return List.of();
            for (final var property : List.of("templateUrl", "styleUrl")) {
                final var resource = JaspringProjectModel.resource(type, JaspringProjectModel.stringValue(annotation, property));
                final var target = resource == null ? null : PsiManager.getInstance(context.getProject()).findFile(resource);
                if (target != null) result.add(new GotoRelatedItem(target, "Jaspring"));
            }
        } else {
            for (final var owner : JaspringProjectModel.owners(file)) result.add(new GotoRelatedItem(owner.type(), "Jaspring"));
        }
        return List.copyOf(result);
    }
}
