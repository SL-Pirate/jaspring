package dev.isira.jaspring.gradle.jnpm;

import java.util.List;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import dev.isira.jaspring.gradle.CompileTypescript;
import dev.isira.jaspring.gradle.JaspringExtension;

public final class JnpmTasks {
    private JnpmTasks() {}

    public static Provider<Boolean> enabled(JnpmExtension extension) {
        return extension.getImplementation().zip(extension.getApi(), (a, b) -> !a.isEmpty() || !b.isEmpty())
                .zip(extension.getDevImplementation(), (a, b) -> a || !b.isEmpty())
                .zip(extension.getDevApi(), (a, b) -> a || !b.isEmpty());
    }

    public static void register(Project project, JaspringExtension jaspring) {
        final var extension = project.getExtensions().create("jnpm", JnpmExtension.class);
        final var active = enabled(extension);
        final var lintActive = active.zip(extension.getLint().getEnabled(), (enabled, lint) -> enabled && lint);
        final Provider<org.gradle.api.file.RegularFile> absentFile = project.getProviders().provider(() -> null);
        final var build = project.getLayout().getBuildDirectory();
        final var projectDir = project.getLayout().getProjectDirectory();
        final var offline = project.getGradle().getStartParameter().isOffline();
        final var cache = project.getGradle().getGradleUserHomeDir().toPath().resolve("caches/jnpm").toFile();
        extension.getNodeHome().convention(project.getProviders().gradleProperty("jnpm.nodeHome").map(projectDir::dir));

        final var generate = project.getTasks().register("generateJnpmManifest", GenerateJnpmManifest.class, task -> {
            task.setGroup("jnpm");
            task.setDescription("Generates npm's internal workspace from the JNPM declarations.");
            task.getImplementation().set(extension.getImplementation());
            task.getApi().set(extension.getApi());
            task.getDevImplementation().set(extension.getDevImplementation());
            task.getDevApi().set(extension.getDevApi());
            task.getLintEnabled().set(extension.getLint().getEnabled());
            task.getRegistries().convention(Map.of());
            task.getProjectDirectory().set(projectDir);
            task.getSourcePath().set(jaspring.getTypescript().getSourceDir().map(dir ->
                    projectDir.getAsFile().toPath().relativize(dir.getAsFile().toPath()).toString().replace(java.io.File.separatorChar, '/')));
            task.getManifestFile().set(build.file("package.json"));
            task.getConfigFile().set(build.file("jnpm/config.json"));
            task.getNpmrcFile().set(build.file(".npmrc"));
            task.onlyIf("JNPM dependencies are declared", ignored -> active.get());
        });
        final var setup = project.getTasks().register("setupJnpm", SetupJnpm.class, task -> {
            task.setGroup("jnpm");
            task.setDescription("Prepares the pinned Node/npm toolchain.");
            task.getNodeHome().set(extension.getNodeHome());
            task.getCacheDir().set(cache);
            task.getOffline().set(offline);
            task.getToolchainFile().set(build.file("jnpm/toolchain.json"));
            task.onlyIf("JNPM dependencies are declared", ignored -> active.get());
            // Revalidate external cache state; the task does no network work once the toolchain exists.
            task.getOutputs().upToDateWhen(ignored -> false);
        });
        project.getTasks().withType(JnpmNpmTask.class).configureEach(task -> {
            task.setGroup("jnpm");
            task.getManifestFile().set(generate.flatMap(GenerateJnpmManifest::getManifestFile));
            task.getNpmrcFile().set(generate.flatMap(GenerateJnpmManifest::getNpmrcFile));
            task.getToolchainFile().set(setup.flatMap(SetupJnpm::getToolchainFile));
            task.getCacheDirectory().set(new java.io.File(cache, "npm"));
            task.getOffline().set(offline);
            task.getTokens().convention(Map.of());
            task.getLockFile().set(projectDir.file("jnpm.lock"));
            task.onlyIf("JNPM dependencies are declared", ignored -> active.get());
        });
        extension.getRegistries().all(registry -> {
            if (!registry.getName().matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("JNPM registry names must contain only letters, digits and underscores");
            generate.configure(task -> task.getRegistries().put(registry.getName(), registry.getUrl().zip(registry.getScopes(),
                    (url, scopes) -> url + "\n" + String.join(",", scopes)).zip(registry.getToken().map(token -> true).orElse(false),
                    (settings, token) -> settings + "\n" + token)));
            project.getTasks().withType(JnpmNpmTask.class).configureEach(task ->
                    task.getTokens().put("JNPM_TOKEN_" + registry.getName(), registry.getToken().orElse("")));
        });
        project.getTasks().register("jnpmLock", JnpmLock.class, task -> {
            task.setDescription("Creates or reconciles jnpm.lock; commit the resulting lockfile.");
            task.getUpdate().set(false);
        });
        project.getTasks().register("jnpmUpdate", JnpmLock.class, task -> {
            task.setDescription("Updates jnpm.lock to newer versions allowed by the JNPM declarations.");
            task.getUpdate().set(true);
        });
        final var install = project.getTasks().register("jnpmInstall", JnpmInstall.class, task -> {
            task.setDescription("Installs the exact JNPM lock into build/node_modules.");
            task.getLockInput().from(task.getLockFile());
            task.getNodeModules().set(build.dir("node_modules"));
            task.mustRunAfter("jnpmLock", "jnpmUpdate");
        });
        final var prepare = project.getTasks().register("prepareJnpmTypes", PrepareJnpmTypes.class, task -> {
            task.setGroup("jnpm");
            task.setDescription("Prepares package declarations, editor configuration and JNPM IDE metadata.");
            task.dependsOn(install);
            task.dependsOn(active.map(value -> value ? List.of("extractTypescriptTypes") : List.of()));
            task.getConfigFile().set(generate.flatMap(GenerateJnpmManifest::getConfigFile));
            task.getPackageFiles().from(install.flatMap(JnpmInstall::getNodeModules).map(dir -> dir.getAsFileTree().matching(pattern ->
                    pattern.include("**/package.json", "**/*.ts", "**/*.mts", "**/*.cts", "**/*.json"))));
            task.getJsonSources().from(jaspring.getTypescript().getSourceDir().map(dir -> dir.getAsFileTree().matching(pattern -> pattern.include("**/*.json"))));
            task.getBaseConfig().set(build.file("jaspring/typescript/types/tsconfig.base.json"));
            task.getGeneratedFiles().from(build.file("jnpm/dependencies.json"), build.file("jnpm/dependencies.dev.json"), build.file("jnpm/ide.json"),
                    build.file("jaspring/typescript/types/tsconfig.jnpm.json"), build.file("jaspring/typescript/types/tsconfig.jnpm.dev.json"),
                    build.file("jaspring/typescript/types/assets.d.ts"));
            task.onlyIf("JNPM dependencies are declared", ignored -> active.get());
            task.getOutputs().upToDateWhen(ignored -> false);
        });
        project.getTasks().named("compileTypescript", CompileTypescript.class, task -> {
            task.dependsOn(prepare);
            task.getJnpmConfigFile().set(active.flatMap(value -> value ? generate.flatMap(GenerateJnpmManifest::getConfigFile) : absentFile));
            task.getJnpmDependencies().set(active.flatMap(value -> value ? build.file("jnpm/dependencies.json") : absentFile));
            task.getJnpmInputs().from(active.map(value -> value ? List.of(projectDir.file("jnpm.lock"), build.file("package-lock.json")) : List.of()));
            task.getJnpmInputs().from(active.map(value -> value ? jaspring.getTypescript().getSourceDir().get().getAsFileTree() : List.of()));
        });
        for (final var name : List.of("lintTypescript", "fixTypescript")) {
            project.getTasks().register(name, LintTypescript.class, task -> {
                task.setGroup("verification");
                task.setDescription(name.equals("fixTypescript") ? "Applies ESLint fixes to browser sources." : "Lints browser sources with the JNPM ESLint configuration.");
                task.dependsOn(prepare);
                task.getConfigFile().set(generate.flatMap(GenerateJnpmManifest::getConfigFile));
                task.getFix().set(name.equals("fixTypescript"));
                task.getSources().from(jaspring.getTypescript().getSourceDir().map(dir -> dir.getAsFileTree()), projectDir.file("eslint.config.mjs"));
                task.getReportFile().set(build.file("reports/jnpm/eslint.json"));
                task.onlyIf("JNPM linting is enabled", ignored -> lintActive.get());
                task.getOutputs().upToDateWhen(ignored -> false);
            });
        }
        project.getTasks().named("check").configure(task -> task.dependsOn("lintTypescript"));
        project.getTasks().matching(task -> task.getName().equals("bootRun")).configureEach(task -> task.dependsOn("compileTypescript"));
    }
}
