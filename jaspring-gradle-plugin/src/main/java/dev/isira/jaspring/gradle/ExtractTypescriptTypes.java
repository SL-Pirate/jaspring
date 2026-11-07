package dev.isira.jaspring.gradle;

import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/// Writes `jaspring.d.ts` and `tsconfig.base.json` for the editor before the first `.ts` file exists. Not part of
/// the build graph: `compileTypescript` writes the same files whenever it runs, and a project without TypeScript
/// must not resolve the compiler at all.
@CacheableTask
public abstract class ExtractTypescriptTypes extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getCompilerClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getTypesDir();

    @TaskAction
    public void extract() throws IOException {
        TypescriptTypes.extract(getCompilerClasspath().getFiles(), getTypesDir().get().getAsFile().toPath());
    }
}
