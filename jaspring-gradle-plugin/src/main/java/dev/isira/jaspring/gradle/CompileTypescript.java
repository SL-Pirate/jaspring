package dev.isira.jaspring.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import dev.isira.jaspring.gradle.browser.GraalvmLocator;

/// Compiles the `.ts` tree with `dev.isira.jaspring.typescript.Cli` from the `jaspringTypescript` configuration,
/// run in a process-isolated worker so GraalJS gets native access and a GraalVM launcher when one is known. The
/// plugin has no compile-time dependency on the compiler: the worker calls the CLI reflectively and the two talk
/// through files. Outputs go to `jaspring/ts/` and the manifest to `jaspring/jaspring-typescript.json` in the
/// resources, where the starter serves and checks them.
@CacheableTask
public abstract class CompileTypescript extends DefaultTask {

    @org.gradle.api.tasks.Optional
    @org.gradle.api.tasks.InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract org.gradle.api.file.RegularFileProperty getJnpmConfigFile();

    @org.gradle.api.tasks.Optional
    @org.gradle.api.tasks.InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract org.gradle.api.file.RegularFileProperty getJnpmDependencies();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getJnpmInputs();

    public static final String MANIFEST = "jaspring-typescript.json";
    static final String CLI_CLASS = "dev.isira.jaspring.typescript.Cli";

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Internal
    public abstract DirectoryProperty getSourceDir();

    /// Put before file names in diagnostics (`src/main/typescript/`), so they are clickable from the project root.
    @Internal
    public abstract Property<String> getSourcePathPrefix();

    @Classpath
    public abstract ConfigurableFileCollection getCompilerClasspath();

    /// An input on purpose: the emitted files are the same either way, so a transpile-only run must not seed the
    /// cache for a type-checked one and hide the errors.
    @Input
    public abstract Property<Boolean> getTypeCheck();

    /// Not inputs: both hosts emit byte-identical output, so cache entries are shared.
    @Internal
    public abstract Property<JaspringExtension.Typescript.Host> getHost();

    @Internal
    public abstract Property<String> getNodePath();

    @Internal
    public abstract Property<String> getGraalvmHome();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @OutputDirectory
    public abstract DirectoryProperty getManifestDir();

    /// `jaspring.d.ts` and `tsconfig.base.json` for the editor, written on every run so they match the compiler used.
    @OutputDirectory
    public abstract DirectoryProperty getTypesDir();

    @Inject
    protected abstract WorkerExecutor getWorkers();

    @Inject
    protected abstract FileSystemOperations getFileSystem();

    @TaskAction
    public void compile() throws IOException {
        final var outputDir = getOutputDir().get().getAsFile().toPath();
        final var manifestDir = getManifestDir().get().getAsFile().toPath();
        if (!getJnpmConfigFile().isPresent()) getFileSystem().delete(spec -> spec.delete(outputDir, manifestDir));
        Files.createDirectories(outputDir);
        Files.createDirectories(manifestDir);
        TypescriptTypes.extract(getCompilerClasspath().getFiles(), getTypesDir().get().getAsFile().toPath());
        final var work = Files.createDirectories(getTemporaryDir().toPath());
        final var diagnostics = work.resolve("diagnostics.txt");
        final var exit = work.resolve("exit.txt");
        Files.deleteIfExists(diagnostics);
        Files.deleteIfExists(exit);

        final var arguments = new ArrayList<>(List.of(
                "--source-dir", getSourceDir().get().getAsFile().getAbsolutePath(),
                "--out", outputDir.toString(),
                "--manifest", manifestDir.resolve(MANIFEST).toString(),
                "--diagnostics", diagnostics.toString(),
                "--host", getHost().get().name().toLowerCase(Locale.ROOT)));
        if (!getTypeCheck().get()) arguments.add("--no-type-check");
        if (getNodePath().isPresent()) arguments.addAll(List.of("--node", getNodePath().get()));
        if (getJnpmDependencies().isPresent()) {
            arguments.addAll(List.of("--dependencies", getJnpmDependencies().get().getAsFile().getAbsolutePath()));
            // Keep the last successful manifest untouched until both checking and bundling succeed.
            arguments.set(arguments.indexOf("--manifest") + 1, work.resolve(MANIFEST).toString());
            dev.isira.jaspring.gradle.jnpm.JnpmBuildSupport.run(getJnpmConfigFile().get().getAsFile().toPath(), "validate", List.of(), getLogger());
        }

        final var queue = getWorkers().processIsolation(spec -> {
            spec.getClasspath().from(getCompilerClasspath());
            spec.forkOptions(fork -> {
                // Truffle needs native access on JDK 24+; without it GraalJS runs interpreted, several times slower.
                fork.jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow");
                fork.setMaxHeapSize("1g");
                // Worker daemons start with an empty environment; the CLI needs the PATH to find Node.
                final var path = System.getenv("PATH");
                if (path != null) fork.environment("PATH", path);
                GraalvmLocator.graalvmHome(getGraalvmHome().map(Path::of).getOrNull())
                        .map(home -> home.resolve("bin").resolve(executable("java")))
                        .filter(Files::isExecutable)
                        .ifPresent(java -> fork.setExecutable(java.toString()));
            });
        });
        queue.submit(CompileTypescriptAction.class, parameters -> {
            parameters.getArguments().set(arguments);
            parameters.getExitFile().set(exit.toFile());
        });
        queue.await();

        final var code = Files.exists(exit) ? Integer.parseInt(Files.readString(exit).trim()) : 2;
        final var prefix = getSourcePathPrefix().getOrElse("");
        var errors = 0;
        if (Files.exists(diagnostics)) {
            for (final var line : Files.readAllLines(diagnostics, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                final var located = !line.startsWith("error TS") && !line.startsWith("warning TS") && !line.startsWith("message TS") && !line.startsWith("suggestion TS");
                final var message = located ? prefix + line : line;
                if (line.contains(": error TS") || line.startsWith("error TS")) {
                    errors++;
                    getLogger().error(message);
                } else {
                    getLogger().warn(message);
                }
            }
        }
        if (code == 1 || errors > 0) {
            throw new GradleException("TypeScript: " + errors + " error(s) in " + prefix.replaceAll("/$", "")
                    + "; fix them or set -Pjaspring.typescript.typeCheck=false to transpile without type checking");
        }
        if (code != 0) {
            throw new GradleException("The TypeScript compiler failed (exit " + code + "); see the messages above."
                    + (getHost().get() == JaspringExtension.Typescript.Host.NODE ? " -Pjaspring.typescript.host=graaljs needs no Node."
                    : " -Pjaspring.typescript.host=node runs the same compiler in Node."));
        }
        if (getJnpmConfigFile().isPresent()) {
            dev.isira.jaspring.gradle.jnpm.JnpmBuildSupport.run(getJnpmConfigFile().get().getAsFile().toPath(), "bundle",
                    List.of(outputDir.toString(), manifestDir.resolve(MANIFEST).toString()), getLogger());
        }
        final var count = getSources().getFiles().size();
        getLogger().lifecycle("{}: {} file(s) compiled from {}", getPath(), count, prefix.replaceAll("/$", ""));
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? name + ".exe" : name;
    }
}
