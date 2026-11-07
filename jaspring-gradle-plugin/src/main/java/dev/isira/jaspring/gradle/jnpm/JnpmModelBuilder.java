package dev.isira.jaspring.gradle.jnpm;

import java.util.Map;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import dev.isira.jaspring.gradle.JaspringExtension;
import dev.isira.jaspring.gradle.jnpm.model.DefaultJnpmModel;
import dev.isira.jaspring.gradle.jnpm.model.JnpmModel;

public final class JnpmModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String name) { return name.equals(JnpmModel.class.getName()); }

    @Override
    public Object buildAll(String name, Project project) {
        final var extension = project.getExtensions().findByType(JnpmExtension.class);
        final var jaspring = project.getExtensions().findByType(JaspringExtension.class);
        final var scopes = extension == null ? Map.<String, Map<String, String>>of() : Map.of(
                "implementation", extension.getImplementation().get(), "api", extension.getApi().get(),
                "devImplementation", extension.getDevImplementation().get(), "devApi", extension.getDevApi().get());
        return new DefaultJnpmModel(extension != null && JnpmTasks.enabled(extension).get(), path(project.getProjectDir()),
                path(project.getRootDir()), path(project.getLayout().getBuildDirectory().get().getAsFile()),
                project.getPath(), jaspring == null ? "" : path(jaspring.getTypescript().getSourceDir().get().getAsFile()),
                extension != null && extension.getLint().getEnabled().get(), scopes);
    }

    private static String path(java.io.File file) {
        return file.getAbsolutePath().replace(java.io.File.separatorChar, '/');
    }
}
