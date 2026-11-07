package dev.isira.jaspring.ide.jnpm.gradle;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import dev.isira.jaspring.ide.jnpm.JnpmProjectService;
import org.jetbrains.annotations.NotNull;

public abstract class JnpmAction extends AnAction implements DumbAware {
    private final String task;
    protected JnpmAction(String task) { this.task = task; }

    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null && !JnpmProjectService.get(event.getProject()).modules().isEmpty());
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        final var project = event.getProject();
        if (project == null) return;
        final var service = JnpmProjectService.get(project);
        final var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        final var selected = file == null ? null : service.forFile(file.getPath());
        if (selected != null) JnpmGradle.run(project, selected, task);
        else for (final var module : service.modules()) JnpmGradle.run(project, module, task);
    }

    public static final class Prepare extends JnpmAction { public Prepare() { super("prepareJnpmTypes"); } }
    public static final class Lock extends JnpmAction { public Lock() { super("jnpmLock"); } }
    public static final class Update extends JnpmAction { public Update() { super("jnpmUpdate"); } }
}
