package dev.isira.jaspring.ide.jnpm.gradle;

import java.util.Collection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.project.Project;
import dev.isira.jaspring.gradle.jnpm.model.JnpmModel;
import dev.isira.jaspring.ide.jnpm.JnpmProjectService;
import org.jetbrains.annotations.NotNull;

public final class JnpmDataService extends AbstractProjectDataService<JnpmModel, Void> {
    @Override public @NotNull Key<JnpmModel> getTargetDataKey() { return JnpmResolver.KEY; }

    @Override
    public void onSuccessImport(@NotNull Collection<DataNode<JnpmModel>> nodes, ProjectData data, @NotNull Project project,
                                @NotNull IdeModelsProvider models) {
        if (data == null) return;
        final var imported = nodes.stream().map(DataNode::getData).toList();
        JnpmProjectService.get(project).replace(data.getLinkedExternalProjectPath(), imported);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            JnpmRuns.configure(project);
            for (final var module : JnpmProjectService.get(project).modules()) {
                if (module.root.equals(data.getLinkedExternalProjectPath())) JnpmGradle.run(project, module, "prepareJnpmTypes");
            }
        }, project.getDisposed());
    }
}
