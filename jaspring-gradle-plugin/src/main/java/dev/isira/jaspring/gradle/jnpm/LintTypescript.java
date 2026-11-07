package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "ESLint configuration may read arbitrary project files; fixes intentionally mutate sources")
public abstract class LintTypescript extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) public abstract RegularFileProperty getConfigFile();
    @Input public abstract Property<Boolean> getFix();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) public abstract ConfigurableFileCollection getSources();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void lint() throws IOException { JnpmBuildSupport.run(getConfigFile().get().getAsFile().toPath(), getFix().get() ? "fix" : "lint", List.of(), getLogger()); }
}
