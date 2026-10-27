package dev.isira.jaspring.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import dev.isira.jaspring.gradle.browser.ClosureRewriter;
import dev.isira.jaspring.gradle.browser.ClosureScanner;
import dev.isira.jaspring.gradle.browser.DispatcherGenerator;
import dev.isira.jaspring.gradle.browser.FoundClosure;
import dev.isira.jaspring.gradle.browser.ManifestWriter;
import dev.isira.jaspring.gradle.browser.TypeShapes;

/// Finds `Platform.onBrowser` closures in the compiled classes, writes the rewritten class copies and the generated
/// dispatcher source for the Web Image build, and `closures.json` (the manifest without assets) for the image task.
@CacheableTask
public abstract class ScanBrowserClosures extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClassesDirs();

    @Classpath
    public abstract ConfigurableFileCollection getAnalysisClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getRewrittenClassesDir();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourcesDir();

    @OutputFile
    public abstract RegularFileProperty getClosuresFile();

    @TaskAction
    public void scan() throws IOException {
        final var classDirs = new ArrayList<Path>();
        getClassesDirs().getFiles().forEach(file -> classDirs.add(file.toPath()));
        final var classpath = new ArrayList<Path>();
        getAnalysisClasspath().getFiles().forEach(file -> classpath.add(file.toPath()));
        final List<FoundClosure> closures;
        try {
            closures = ClosureScanner.scan(classDirs, classpath);
        } catch (IllegalArgumentException e) {
            throw new GradleException("Browser closure cannot be compiled: " + e.getMessage(), e);
        }

        final var rewritten = getRewrittenClassesDir().get().getAsFile().toPath();
        final var sources = getGeneratedSourcesDir().get().getAsFile().toPath();
        clear(rewritten);
        clear(sources);
        if (!closures.isEmpty()) {
            ClosureRewriter.rewrite(closures, new TypeShapes(classDirs, classpath), rewritten);
            final var entry = sources.resolve(DispatcherGenerator.PACKAGE.replace('.', '/')).resolve(DispatcherGenerator.CLASS_NAME + ".java");
            Files.createDirectories(entry.getParent());
            Files.writeString(entry, DispatcherGenerator.generate(closures));
        }
        final var closuresFile = getClosuresFile().get().getAsFile().toPath();
        Files.createDirectories(closuresFile.getParent());
        Files.writeString(closuresFile, ManifestWriter.write(closures, null, null, null));
        getLogger().lifecycle("{}: {} browser closure(s)", getPath(), closures.size());
    }

    /// Whether the last scan found closures, read by the image tasks' `onlyIf`.
    public static boolean foundClosures(Path closuresFile) {
        try {
            return Files.isRegularFile(closuresFile) && Files.readString(closuresFile).contains("\"implClass\"");
        } catch (IOException e) {
            return false;
        }
    }

    private static void clear(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            try (var files = Files.walk(directory)) {
                files.sorted(java.util.Comparator.reverseOrder()).filter(path -> !path.equals(directory)).forEach(path -> path.toFile().delete());
            }
        }
        Files.createDirectories(directory);
    }
}
