package dev.isira.jaspring.ide.jnpm.gradle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import dev.isira.jaspring.ide.jnpm.JnpmProjectService;
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class JnpmRuns implements RunManagerListener {
    @Override public void runConfigurationAdded(RunnerAndConfigurationSettings settings) { configure(settings); }
    @Override public void runConfigurationChanged(RunnerAndConfigurationSettings settings) { configure(settings); }

    static void configure(Project project) {
        for (final var settings : RunManager.getInstance(project).getAllSettings()) configure(settings);
    }

    private static void configure(RunnerAndConfigurationSettings settings) {
        if (!(settings.getConfiguration() instanceof ModuleBasedConfiguration<?, ?> configuration)) return;
        final var module = configuration.getConfigurationModule().getModule();
        if (module == null) return;
        final var project = configuration.getProject();
        for (final var jnpm : JnpmProjectService.get(project).modules()) {
            final var ownsModule = java.util.Arrays.stream(ModuleRootManager.getInstance(module).getContentRoots())
                    .anyMatch(root -> Path.of(root.getPath()).startsWith(Path.of(jnpm.project)));
            if (!ownsModule) continue;
            final var tasks = new ArrayList<>(configuration.getBeforeRunTasks());
            final var taskName = jnpm.task("compileTypescript");
            if (tasks.stream().anyMatch(task -> task instanceof ExternalSystemBeforeRunTask external
                    && jnpm.root.equals(external.getTaskExecutionSettings().getExternalProjectPath())
                    && external.getTaskExecutionSettings().getTaskNames().stream().anyMatch(name -> name.equals(taskName)
                    || name.equals(jnpm.task("build")) || name.equals(jnpm.task("classes"))))) return;
            final var task = new ExternalSystemBeforeRunTask(GradleBeforeRunTaskProvider.ID, GradleConstants.SYSTEM_ID);
            task.setEnabled(true);
            task.getTaskExecutionSettings().setExternalProjectPath(jnpm.root);
            task.getTaskExecutionSettings().setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
            task.getTaskExecutionSettings().setTaskNames(List.of(taskName));
            tasks.add(task);
            configuration.setBeforeRunTasks(tasks);
            return;
        }
    }
}
