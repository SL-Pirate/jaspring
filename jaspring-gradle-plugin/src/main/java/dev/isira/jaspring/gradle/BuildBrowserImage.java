package dev.isira.jaspring.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import dev.isira.jaspring.gradle.browser.ClosureScanner;
import dev.isira.jaspring.gradle.browser.DispatcherGenerator;
import dev.isira.jaspring.gradle.browser.GraalvmLocator;
import dev.isira.jaspring.gradle.browser.ManifestWriter;

/// Runs `native-image --tool:svm-wasm` over the generated dispatcher and the rewritten classes, then writes the
/// launcher, the module and `jaspring/jaspring-browser.json` for `processResources` to pick up.
@CacheableTask
public abstract class BuildBrowserImage extends DefaultTask {

    public static final String LAUNCHER = "jaspring-app.js";
    public static final String MODULE = "jaspring-app.js.wasm";
    public static final String MANIFEST = "jaspring-browser.json";

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDispatcherClasses();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getRewrittenClasses();

    @Classpath
    public abstract ConfigurableFileCollection getImageClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getClosuresFile();

    /// The scanned class directories, re-read to rebuild the closure list (the scan is cheap and deterministic).
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClassesDirs();

    @Classpath
    public abstract ConfigurableFileCollection getAnalysisClasspath();

    @Input
    @Optional
    public abstract Property<String> getGraalvmHome();

    @Input
    @Optional
    public abstract Property<String> getAssemblerPath();

    /// Tool versions, so a toolchain change invalidates the cached image.
    @Input
    public abstract Property<String> getToolchainFingerprint();

    @Input
    public abstract ListProperty<String> getNativeImageArgs();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @OutputDirectory
    public abstract DirectoryProperty getManifestDir();

    @Inject
    protected abstract ExecOperations getExec();

    @TaskAction
    public void build() throws IOException {
        final var toolchain = locate();
        final var output = getOutputDir().get().getAsFile().toPath();
        final var manifestDir = getManifestDir().get().getAsFile().toPath();
        Files.createDirectories(output);
        Files.createDirectories(manifestDir);
        final var work = Files.createTempDirectory(getTemporaryDir().toPath(), "image");

        final var classpath = new ArrayList<Path>();
        getDispatcherClasses().getFiles().forEach(file -> classpath.add(file.toPath()));
        getRewrittenClasses().getFiles().forEach(file -> classpath.add(file.toPath()));
        getImageClasspath().getFiles().forEach(file -> classpath.add(file.toPath()));

        final var arguments = new ArrayList<String>();
        arguments.add(toolchain.nativeImage().toString());
        arguments.add("--tool:svm-wasm");
        arguments.add("-H:+UnlockExperimentalVMOptions");
        arguments.add("-H:-AutoRunVM");
        arguments.add("-H:VMClassName=JaspringWasm");
        arguments.add("-H:WasmAsPath=" + toolchain.wasmAs());
        arguments.addAll(getNativeImageArgs().get());
        arguments.add("-cp");
        arguments.add(classpath.stream().map(Path::toString).collect(Collectors.joining(java.io.File.pathSeparator)));
        arguments.add("-o");
        arguments.add("jaspring-app");
        arguments.add(DispatcherGenerator.MAIN_CLASS);

        final var log = new ByteArrayOutputStream();
        final var result = getExec().exec(spec -> {
            spec.setWorkingDir(work.toFile());
            spec.setCommandLine(arguments);
            spec.setStandardOutput(log);
            spec.setErrorOutput(log);
            spec.setIgnoreExitValue(true);
        });
        getLogger().info(log.toString());
        if (result.getExitValue() != 0) {
            throw new GradleException("native-image --tool:svm-wasm failed (exit " + result.getExitValue() + "):\n" + log);
        }

        final var launcher = Files.copy(work.resolve(LAUNCHER), output.resolve(LAUNCHER), StandardCopyOption.REPLACE_EXISTING);
        final var module = Files.copy(work.resolve(MODULE), output.resolve(MODULE), StandardCopyOption.REPLACE_EXISTING);
        final var closures = scanAgain();
        final var manifest = ManifestWriter.write(closures, asset(launcher), asset(module), getToolchainFingerprint().get());
        Files.writeString(manifestDir.resolve(MANIFEST), manifest);
        getLogger().lifecycle("{}: {} ({} bytes), {} ({} bytes)", getPath(), LAUNCHER, Files.size(launcher), MODULE, Files.size(module));
    }

    private GraalvmLocator.Toolchain locate() {
        try {
            return GraalvmLocator.locate(getGraalvmHome().map(Path::of).getOrNull(), getAssemblerPath().map(Path::of).getOrNull());
        } catch (IllegalStateException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    private List<dev.isira.jaspring.gradle.browser.FoundClosure> scanAgain() throws IOException {
        final var dirs = new ArrayList<Path>();
        getClassesDirs().getFiles().forEach(file -> dirs.add(file.toPath()));
        final var classpath = new ArrayList<Path>();
        getAnalysisClasspath().getFiles().forEach(file -> classpath.add(file.toPath()));
        return ClosureScanner.scan(dirs, classpath);
    }

    private static ManifestWriter.Asset asset(Path file) throws IOException {
        try {
            final var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return new ManifestWriter.Asset(file.getFileName().toString(), HexFormat.of().formatHex(digest), Files.size(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
