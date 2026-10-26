package dev.isira.jaspring.ide;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;

import java.util.LinkedHashSet;
import java.util.List;

public final class JaspringLanguageSubstitutor extends LanguageSubstitutor {
    @Override
    public Language getLanguage(VirtualFile file, Project project) {
        // Called by the indexer itself. Resolving annotations here can deadlock index updates.
        if (!file.getName().endsWith(".component.html")) return null;
        final var module = ProjectFileIndex.getInstance(project).getModuleForFile(file);
        if (module == null) return null;
        final var manager = ModuleRootManager.getInstance(module);
        final var roots = new LinkedHashSet<>(List.of(manager.getSourceRoots()));
        roots.addAll(List.of(manager.orderEntries().recursively().sources().getRoots()));
        roots.addAll(List.of(manager.orderEntries().recursively().classes().getRoots()));
        for (final var root : roots) {
            if (root.findFileByRelativePath("dev/isira/jaspring/component/Component.java") != null
                    || root.findFileByRelativePath("dev/isira/jaspring/component/Component.class") != null) {
                return JaspringHtmlLanguage.INSTANCE;
            }
        }
        return null;
    }
}
