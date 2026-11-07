package dev.isira.jaspring.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;

import dev.isira.jaspring.gradle.browser.GraalvmLocator;

/// Scaffolding tasks, plus two builds for projects that apply the `java` plugin: the WebAssembly build of
/// `Platform.onBrowser` closures (scan the compiled classes, compile the generated dispatcher with the GraalVM JDK,
/// build the image with Web Image) and the TypeScript build of `src/main/typescript`. Both ship their outputs and a
/// manifest as resources.
public final class JaspringPlugin implements Plugin<Project> {

    @javax.inject.Inject
    public JaspringPlugin(org.gradle.tooling.provider.model.ToolingModelBuilderRegistry models) {
        models.register(new dev.isira.jaspring.gradle.jnpm.JnpmModelBuilder());
    }

    public static final String BROWSER_CONFIGURATION = "jaspringBrowser";
    public static final String TYPESCRIPT_CONFIGURATION = "jaspringTypescript";

    @Override
    public void apply(Project project) {
        project.getTasks().register("generateComponent", GenerateComponent.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Generates a Jaspring Java component, HTML template and CSS stylesheet.");
            task.getJavaDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/java"));
            task.getResourcesDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/resources"));
        });
        project.getPlugins().withType(JavaPlugin.class, plugin -> {
            final var extension = project.getExtensions().create("jaspring", JaspringExtension.class);
            browserBuild(project, extension);
            typescriptBuild(project, extension);
            dev.isira.jaspring.gradle.jnpm.JnpmTasks.register(project, extension);
        });
    }

    private static void browserBuild(Project project, JaspringExtension extension) {
        final var browser = extension.getBrowser();
        final var providers = project.getProviders();
        browser.getSkip().convention(providers.gradleProperty("jaspring.browser.skip").map(Boolean::parseBoolean).orElse(false));
        browser.getGraalvmHome().convention(providers.gradleProperty("jaspring.browser.graalvmHome").map(project.getLayout().getProjectDirectory()::dir));
        browser.getAssemblerPath().convention(providers.gradleProperty("jaspring.browser.assemblerPath").map(project.getLayout().getProjectDirectory()::file));
        browser.getNativeImageArgs().convention(List.of());

        final var runtime = project.getConfigurations().create(BROWSER_CONFIGURATION, configuration -> {
            configuration.setDescription("The jaspring-browser-runtime jar compiled into the WebAssembly image.");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            pluginVersion().ifPresent(version -> configuration.defaultDependencies(dependencies ->
                    dependencies.add(project.getDependencies().create("dev.isira:jaspring-browser-runtime:" + version))));
        });

        final var main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final var buildDir = project.getLayout().getBuildDirectory().dir("jaspring/browser");

        final var scan = project.getTasks().register("scanBrowserClosures", ScanBrowserClosures.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Finds Platform.onBrowser closures and prepares the WebAssembly build inputs.");
            task.getClassesDirs().from(main.getOutput().getClassesDirs());
            task.getAnalysisClasspath().from(main.getCompileClasspath());
            task.getRewrittenClassesDir().convention(buildDir.map(dir -> dir.dir("classes")));
            task.getGeneratedSourcesDir().convention(buildDir.map(dir -> dir.dir("src")));
            task.getClosuresFile().convention(buildDir.map(dir -> dir.file("closures.json")));
        });

        final var compile = project.getTasks().register("compileBrowserDispatcher", JavaCompile.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Compiles the generated WebAssembly dispatcher against the GraalVM Web Image API.");
            task.dependsOn(scan);
            task.source(scan.flatMap(ScanBrowserClosures::getGeneratedSourcesDir));
            task.setClasspath(project.files(scan.flatMap(ScanBrowserClosures::getRewrittenClassesDir), runtime, main.getCompileClasspath()));
            task.getDestinationDirectory().set(buildDir.map(dir -> dir.dir("dispatcher")));
            task.getOptions().getCompilerArgs().addAll(List.of("--add-modules", "org.graalvm.webimage.api", "-parameters"));
            task.getOptions().setFork(true);
            task.getOptions().getForkOptions().setExecutable(browser.getGraalvmHome().map(dir -> dir.getAsFile().toPath())
                    .orElse(providers.provider(() -> GraalvmLocator.graalvmHome(null).orElse(null)))
                    .map(home -> home.resolve("bin").resolve("javac").toString()).getOrNull());
            final var closuresFile = scan.flatMap(ScanBrowserClosures::getClosuresFile);
            task.onlyIf("closures were found and the WebAssembly build is not skipped", t -> {
                if (browser.getSkip().get()) {
                    if (ScanBrowserClosures.foundClosures(closuresFile.get().getAsFile().toPath())) {
                        t.getLogger().warn("{}: Platform.onBrowser closures exist but jaspring.browser.skip is set; they will fail at runtime", t.getPath());
                    }
                    return false;
                }
                return ScanBrowserClosures.foundClosures(closuresFile.get().getAsFile().toPath());
            });
        });

        final var image = project.getTasks().register("buildBrowserImage", BuildBrowserImage.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Builds the WebAssembly module of Platform.onBrowser closures with GraalVM Web Image.");
            task.dependsOn(compile);
            task.getDispatcherClasses().from(compile.map(JavaCompile::getDestinationDirectory));
            task.getRewrittenClasses().from(scan.flatMap(ScanBrowserClosures::getRewrittenClassesDir));
            // Not the runtime classpath: that depends on `classes`, which depends on processResources, which waits for this task.
            // Only the application's classes, sibling projects (directories), Jaspring's jars and the browser runtime
            // are compiled; other library jars bring Native Image metadata the WebAssembly backend cannot honour.
            task.getImageClasspath().from(main.getOutput().getClassesDirs(),
                    project.files(runtime, main.getCompileClasspath()).filter(file -> file.isDirectory() || file.getName().startsWith("jaspring-")),
                    browser.getImageClasspath());
            task.getClosuresFile().set(scan.flatMap(ScanBrowserClosures::getClosuresFile));
            task.getClassesDirs().from(main.getOutput().getClassesDirs());
            task.getAnalysisClasspath().from(main.getCompileClasspath());
            task.getGraalvmHome().set(browser.getGraalvmHome().map(dir -> dir.getAsFile().getAbsolutePath()));
            task.getAssemblerPath().set(browser.getAssemblerPath().map(file -> file.getAsFile().getAbsolutePath()));
            task.getNativeImageArgs().set(browser.getNativeImageArgs());
            task.getToolchainFingerprint().set(providers.provider(() -> fingerprint(browser)));
            task.getOutputDir().convention(buildDir.map(dir -> dir.dir("image")));
            task.getManifestDir().convention(buildDir.map(dir -> dir.dir("manifest")));
            final var closuresFile = scan.flatMap(ScanBrowserClosures::getClosuresFile);
            task.onlyIf("closures were found and the WebAssembly build is not skipped",
                    t -> !browser.getSkip().get() && ScanBrowserClosures.foundClosures(closuresFile.get().getAsFile().toPath()));
        });

        project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources.class, task -> {
            task.dependsOn(image);
            task.from(image.map(BuildBrowserImage::getOutputDir), spec -> spec.into("jaspring/wasm"));
            task.from(image.map(BuildBrowserImage::getManifestDir), spec -> spec.into("jaspring"));
        });
    }

    private static void typescriptBuild(Project project, JaspringExtension extension) {
        final var typescript = extension.getTypescript();
        final var providers = project.getProviders();
        final var projectDir = project.getLayout().getProjectDirectory();
        typescript.getSourceDir().convention(projectDir.dir("src/main/typescript"));
        typescript.getTypeCheck().convention(providers.gradleProperty("jaspring.typescript.typeCheck").map(Boolean::parseBoolean).orElse(true));
        typescript.getHost().convention(providers.gradleProperty("jaspring.typescript.host")
                .map(value -> JaspringExtension.Typescript.Host.valueOf(value.toUpperCase(java.util.Locale.ROOT)))
                .orElse(JaspringExtension.Typescript.Host.GRAALJS));
        typescript.getNodePath().convention(providers.gradleProperty("jaspring.typescript.nodePath").map(projectDir::file));
        typescript.getSkip().convention(providers.gradleProperty("jaspring.typescript.skip").map(Boolean::parseBoolean).orElse(false));

        final var compiler = project.getConfigurations().create(TYPESCRIPT_CONFIGURATION, configuration -> {
            configuration.setDescription("The jaspring-typescript compiler (the bundled TypeScript compiler on GraalJS or Node) and its runtime.");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            pluginVersion().ifPresent(version -> configuration.defaultDependencies(dependencies ->
                    dependencies.add(project.getDependencies().create("dev.isira:jaspring-typescript:" + version))));
        });

        final var buildDir = project.getLayout().getBuildDirectory().dir("jaspring/typescript");
        final var typesDir = buildDir.map(dir -> dir.dir("types"));
        // Explicit only: a project without TypeScript must never resolve the compiler, so nothing depends on this task.
        project.getTasks().register("extractTypescriptTypes", ExtractTypescriptTypes.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Extracts jaspring.d.ts and tsconfig.base.json from the compiler for editors, before the first .ts file exists.");
            task.getCompilerClasspath().from(compiler);
            task.getTypesDir().convention(typesDir);
        });

        // Only the .ts tree and an external configuration are inputs: nothing from `classes`, which waits for processResources.
        final var compile = project.getTasks().register("compileTypescript", CompileTypescript.class, task -> {
            task.setGroup("jaspring");
            task.setDescription("Compiles src/main/typescript to ES modules served under /_jaspring/ts/.");
            task.getTypesDir().convention(typesDir);
            task.getSourceDir().set(typescript.getSourceDir());
            task.getSources().from(typescript.getSourceDir().map(dir -> dir.getAsFileTree().matching(pattern -> pattern.include("**/*.ts"))));
            task.getSourcePathPrefix().set(typescript.getSourceDir().map(dir ->
                    projectDir.getAsFile().toPath().relativize(dir.getAsFile().toPath()).toString().replace(java.io.File.separatorChar, '/') + "/"));
            task.getCompilerClasspath().from(compiler);
            task.getTypeCheck().set(typescript.getTypeCheck());
            task.getHost().set(typescript.getHost());
            task.getNodePath().set(typescript.getNodePath().map(file -> file.getAsFile().getAbsolutePath()));
            task.getGraalvmHome().set(extension.getBrowser().getGraalvmHome().map(dir -> dir.getAsFile().getAbsolutePath()));
            task.getOutputDir().convention(buildDir.map(dir -> dir.dir("js")));
            task.getManifestDir().convention(buildDir.map(dir -> dir.dir("manifest")));
            task.onlyIf("the TypeScript build is not skipped", t -> {
                if (!typescript.getSkip().get()) return true;
                t.getLogger().warn("{}: jaspring.typescript.skip is set; the page scripts in {} are not compiled", t.getPath(),
                        typescript.getSourceDir().get().getAsFile());
                return false;
            });
        });

        project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources.class, task -> {
            task.dependsOn(compile);
            task.from(compile.map(CompileTypescript::getOutputDir), spec -> spec.into("jaspring/ts"));
            task.from(compile.map(CompileTypescript::getManifestDir), spec -> spec.into("jaspring"));
        });
    }

    /// Tool versions that must invalidate a cached image; empty when the toolchain is absent (the task then fails
    /// with the locator's explanation instead of at configuration time).
    private static String fingerprint(JaspringExtension.Browser browser) {
        if (browser.getSkip().get()) return "";
        try {
            final var toolchain = GraalvmLocator.locate(browser.getGraalvmHome().map(dir -> dir.getAsFile().toPath()).getOrNull(),
                    browser.getAssemblerPath().map(file -> file.getAsFile().toPath()).getOrNull());
            return GraalvmLocator.version(toolchain.nativeImage()) + "; " + GraalvmLocator.version(toolchain.wasmAs());
        } catch (IllegalStateException e) {
            return "";
        }
    }

    static java.util.Optional<String> pluginVersion() {
        try (InputStream in = JaspringPlugin.class.getResourceAsStream("/META-INF/jaspring/plugin.properties")) {
            if (in == null) return java.util.Optional.empty();
            final var properties = new Properties();
            properties.load(in);
            final var version = properties.getProperty("version");
            return version == null || version.contains("${") ? java.util.Optional.empty() : java.util.Optional.of(version);
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    static Path path(String value) {
        return Path.of(value);
    }
}
