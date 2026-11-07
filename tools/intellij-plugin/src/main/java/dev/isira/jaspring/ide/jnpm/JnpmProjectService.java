package dev.isira.jaspring.ide.jnpm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import dev.isira.jaspring.gradle.jnpm.model.JnpmModel;
import org.jetbrains.annotations.NotNull;

/// Only Gradle's imported model establishes a JNPM module. No build-script parsing or directory guessing.
@Service(Service.Level.PROJECT)
@State(name = "JaspringJnpm", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class JnpmProjectService implements PersistentStateComponent<JnpmProjectService.Settings> {
    private Settings settings = new Settings();

    public static JnpmProjectService get(Project project) { return project.getService(JnpmProjectService.class); }

    public synchronized void replace(String root, List<JnpmModel> models) {
        final var next = new ArrayList<>(settings.modules.stream().filter(module -> !module.root.equals(root)).toList());
        for (final var model : models) if (model.getEnabled()) {
            final var module = new Module();
            module.root = model.getRootDirectory();
            module.project = model.getProjectDirectory();
            module.build = model.getBuildDirectory();
            module.source = model.getSourceDirectory();
            module.gradlePath = model.getGradlePath();
            module.lint = model.getLintEnabled();
            next.add(module);
        }
        settings.modules = next;
    }

    public synchronized List<Module> modules() { return List.copyOf(settings.modules); }

    public synchronized Module forFile(String file) {
        final var path = Path.of(file);
        return settings.modules.stream().filter(module -> path.startsWith(Path.of(module.source))).findFirst().orElse(null);
    }

    @Override public synchronized @NotNull Settings getState() { return settings; }
    @Override public synchronized void loadState(@NotNull Settings state) { settings = state; }

    public static final class Settings { public List<Module> modules = new ArrayList<>(); }

    public static final class Module {
        public String root = "";
        public String project = "";
        public String build = "";
        public String source = "";
        public String gradlePath = "";
        public boolean lint;

        public String task(String name) { return gradlePath.equals(":") ? ":" + name : gradlePath + ":" + name; }
        public Path metadata() { return Path.of(build, "jnpm", "ide.json"); }
    }
}
