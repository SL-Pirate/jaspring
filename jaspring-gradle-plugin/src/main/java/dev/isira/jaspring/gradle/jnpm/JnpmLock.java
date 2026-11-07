package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Explicitly updates the committed npm lock against a registry")
public abstract class JnpmLock extends JnpmNpmTask {
    @Input public abstract Property<Boolean> getUpdate();

    @TaskAction
    public void resolve() throws IOException { npm(getUpdate().get() ? "update" : "install"); }
}
