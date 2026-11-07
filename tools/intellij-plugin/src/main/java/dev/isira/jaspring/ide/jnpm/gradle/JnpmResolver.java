package dev.isira.jaspring.ide.jnpm.gradle;

import java.nio.file.Path;
import java.util.Set;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.roots.DependencyScope;
import dev.isira.jaspring.gradle.jnpm.model.JnpmModel;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class JnpmResolver extends AbstractProjectResolverExtension {
    public static final Key<JnpmModel> KEY = Key.create(JnpmModel.class, ProjectKeys.MODULE.getProcessingWeight() + 1);

    @Override
    public Set<Class<?>> getExtraProjectModelClasses() { return Set.of(JnpmModel.class); }

    @Override
    public Set<Class<?>> getToolingExtensionsClasses() { return Set.of(JnpmModel.class); }

    @Override
    public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
        final var model = resolverCtx.getProjectModel(gradleModule.getGradleProject(), JnpmModel.class);
        if (model != null) ideModule.createChild(KEY, model);
        nextResolver.populateModuleExtraModels(gradleModule, ideModule);
    }

    @Override
    public void populateModuleDependencies(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule,
                                          @NotNull DataNode<ProjectData> ideProject) {
        nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
        final var model = resolverCtx.getProjectModel(gradleModule.getGradleProject(), JnpmModel.class);
        if (model == null || !model.getEnabled()) return;
        model.getScopes().forEach((scope, dependencies) -> dependencies.forEach((name, version) -> {
            final var library = new LibraryData(GradleConstants.SYSTEM_ID, "JNPM " + name + " " + version);
            final var directory = Path.of(model.getBuildDirectory(), "node_modules", name).toString();
            library.addPath(LibraryPathType.SOURCE, directory);
            library.addPath(LibraryPathType.BINARY, directory);
            final var dependency = new LibraryDependencyData(ideModule.getData(), library, LibraryLevel.MODULE);
            dependency.setScope(scope.startsWith("dev") ? DependencyScope.TEST : DependencyScope.COMPILE);
            ideModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, dependency);
        }));
    }
}
