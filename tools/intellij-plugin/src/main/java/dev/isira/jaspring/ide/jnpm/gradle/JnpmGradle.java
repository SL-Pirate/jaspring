package dev.isira.jaspring.ide.jnpm.gradle;

import java.util.List;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import dev.isira.jaspring.ide.jnpm.JnpmProjectService;
import org.jetbrains.plugins.gradle.util.GradleConstants;

final class JnpmGradle {
    private JnpmGradle() {}

    static void run(Project project, JnpmProjectService.Module module, String task) {
        if (!com.intellij.ide.trustedProjects.TrustedProjects.isProjectTrusted(project)) return;
        final var settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
        settings.setExternalProjectPath(module.root);
        settings.setTaskNames(task.equals("jnpmLock") || task.equals("jnpmUpdate")
                ? List.of(module.task(task), module.task("prepareJnpmTypes")) : List.of(module.task(task)));
        ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID, new TaskCallback() {
            @Override public void onSuccess() { LocalFileSystem.getInstance().refreshIoFiles(List.of(new java.io.File(module.build)), true, true, null); }
            @Override public void onFailure() { /* Gradle's build window contains the actionable diagnostic. */ }
        }, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false);
    }
}
