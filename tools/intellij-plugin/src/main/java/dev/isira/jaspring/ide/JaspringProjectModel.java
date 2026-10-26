package dev.isira.jaspring.ide;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class JaspringProjectModel {
    static final String COMPONENT = "dev.isira.jaspring.component.Component";
    static final String DIRECTIVE = "dev.isira.jaspring.component.Directive";
    static final String PIPE = "dev.isira.jaspring.component.Pipe";
    static final String INPUT = "dev.isira.jaspring.component.Input";
    static final String OUTPUT = "dev.isira.jaspring.component.Output";

    record Declaration(PsiClass type, PsiAnnotation annotation, String selector, String pipeName) {
        boolean isComponent() { return annotation.hasQualifiedName(COMPONENT); }
    }

    private JaspringProjectModel() {}

    static List<Declaration> declarations(Project project) {
        if (DumbService.isDumb(project)) return List.of();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            final var result = new ArrayList<Declaration>();
            final var scope = GlobalSearchScope.allScope(project);
            for (final var name : List.of(COMPONENT, DIRECTIVE, PIPE)) {
                final var annotationType = JavaPsiFacade.getInstance(project).findClass(name, scope);
                if (annotationType == null) continue;
                for (final var type : AnnotatedElementsSearch.searchPsiClasses(annotationType, scope)) {
                    final var annotation = type.getAnnotation(name);
                    if (annotation != null) {
                        result.add(new Declaration(type, annotation, stringValue(annotation, "selector"), stringValue(annotation, "name")));
                    }
                }
            }
            return CachedValueProvider.Result.create(List.copyOf(result), PsiModificationTracker.MODIFICATION_COUNT,
                    ProjectRootModificationTracker.getInstance(project));
        });
    }

    static List<Declaration> visibleDeclarations(PsiFile file) {
        final var module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module == null) return declarations(file.getProject());
        final var virtualFile = file.getOriginalFile().getVirtualFile();
        final var includeTests = virtualFile != null && ProjectFileIndex.getInstance(file.getProject()).isInTestSourceContent(virtualFile);
        final var scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
        return declarations(file.getProject()).stream().filter(d -> {
            final var source = d.type().getContainingFile().getVirtualFile();
            return source != null && scope.contains(source);
        }).toList();
    }

    static List<Declaration> owners(PsiFile file) {
        final var host = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);
        if (host != null) {
            final var annotation = PsiTreeUtil.getParentOfType(host, PsiAnnotation.class);
            final var type = PsiTreeUtil.getParentOfType(host, PsiClass.class);
            if (annotation != null && type != null && annotation.hasQualifiedName(COMPONENT)) {
                return List.of(new Declaration(type, annotation, stringValue(annotation, "selector"), ""));
            }
        }
        final var virtualFile = file.getOriginalFile().getVirtualFile();
        return owners(file.getProject(), virtualFile);
    }

    static List<Declaration> owners(Project project, VirtualFile file) {
        if (file == null || !"html".equalsIgnoreCase(file.getExtension())) return List.of();
        return declarations(project).stream().filter(Declaration::isComponent)
                .filter(d -> file.equals(resource(d.type(), stringValue(d.annotation(), "templateUrl")))).toList();
    }

    static String stringValue(PsiAnnotation annotation, String name) {
        final var value = annotation.findAttributeValue(name);
        if (value == null) return "";
        final var result = JavaPsiFacade.getInstance(annotation.getProject()).getConstantEvaluationHelper()
                .computeConstantExpression(value);
        return result instanceof String text ? text : "";
    }

    /// Mirrors ComponentDefs.resourcePath, including package-relative paths across Java/resource roots.
    static String resourcePath(String packageName, String url) {
        final var segments = new ArrayDeque<String>();
        if (!url.startsWith("/") && !packageName.isEmpty()) segments.addAll(List.of(packageName.split("\\.")));
        for (final var part : url.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (!part.equals("..")) segments.add(part);
            else if (segments.pollLast() == null) return null;
        }
        return String.join("/", segments);
    }

    static VirtualFile resource(PsiClass type, String url) {
        if (url.isEmpty()) return null;
        final var file = type.getContainingFile();
        final var packageName = file instanceof PsiJavaFile javaFile ? javaFile.getPackageName() : "";
        final var path = resourcePath(packageName, url);
        final var module = ModuleUtilCore.findModuleForPsiElement(type);
        if (path == null || module == null) return null;
        final var roots = new LinkedHashSet<VirtualFile>();
        final var manager = ModuleRootManager.getInstance(module);
        roots.addAll(List.of(manager.getSourceRoots()));
        roots.addAll(List.of(manager.orderEntries().recursively().sources().getRoots()));
        roots.addAll(List.of(manager.orderEntries().recursively().classes().getRoots()));
        for (final var root : roots) {
            final var resource = root.findFileByRelativePath(path);
            if (resource != null && !resource.isDirectory()) return resource;
        }
        return null;
    }
}
