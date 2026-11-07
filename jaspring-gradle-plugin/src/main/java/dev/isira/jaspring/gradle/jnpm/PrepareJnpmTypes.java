package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "IDE metadata includes paths to the local toolchain")
public abstract class PrepareJnpmTypes extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) public abstract RegularFileProperty getConfigFile();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) public abstract ConfigurableFileCollection getPackageFiles();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) public abstract ConfigurableFileCollection getJsonSources();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getBaseConfig();
    @OutputFiles public abstract ConfigurableFileCollection getGeneratedFiles();

    @TaskAction
    public void prepare() throws IOException { JnpmBuildSupport.run(getConfigFile().get().getAsFile().toPath(), "prepare", List.of(), getLogger()); }
}
