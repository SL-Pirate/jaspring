package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "node_modules includes platform-dependent executables and links")
public abstract class JnpmInstall extends JnpmNpmTask {
    @InputFiles @PathSensitive(PathSensitivity.NONE) public abstract ConfigurableFileCollection getLockInput();
    @OutputDirectory public abstract DirectoryProperty getNodeModules();

    @TaskAction
    public void install() throws IOException { npm("ci"); }
}
