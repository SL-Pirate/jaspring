package dev.isira.jaspring.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/// Adds explicit scaffolding tasks without changing the application's dependencies or build lifecycle.
public final class JaspringPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("generateComponent", GenerateComponent.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Generates a Jaspring Java component, HTML template and CSS stylesheet.");
            task.getJavaDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/java"));
            task.getResourcesDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/resources"));
        });
    }
}
