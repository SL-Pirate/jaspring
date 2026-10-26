package dev.isira.jaspring.ide;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

public final class JaspringResourceInspection extends LocalInspectionTool {
    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(PsiAnnotation annotation) {
                if (DumbService.isDumb(annotation.getProject()) || !annotation.hasQualifiedName(JaspringProjectModel.COMPONENT)) return;
                final var type = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
                if (type == null) return;
                for (final var property : List.of("templateUrl", "styleUrl")) {
                    final var path = JaspringProjectModel.stringValue(annotation, property);
                    final var value = annotation.findDeclaredAttributeValue(property);
                    if (value != null && !path.isEmpty() && JaspringProjectModel.resource(type, path) == null) {
                        holder.registerProblem(value, "Jaspring resource not found: " + path);
                    }
                }
            }
        };
    }
}
