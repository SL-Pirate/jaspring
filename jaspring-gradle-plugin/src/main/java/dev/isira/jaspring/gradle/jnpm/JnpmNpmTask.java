package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "npm owns its download cache; installs and lock updates must run locally")
public abstract class JnpmNpmTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getManifestFile();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getNpmrcFile();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getToolchainFile();
    @Internal public abstract DirectoryProperty getCacheDirectory();
    @Internal public abstract Property<Boolean> getOffline();
    @Internal public abstract MapProperty<String, String> getTokens();
    @Internal public abstract RegularFileProperty getLockFile();

    final void npm(String command) throws IOException {
        final var workspace = getManifestFile().get().getAsFile().toPath().getParent();
        final var lock = getLockFile().get().getAsFile().toPath();
        final var installedLock = workspace.resolve("package-lock.json");
        if (command.equals("ci") && !Files.exists(lock)) throw new GradleException("Missing jnpm.lock; run jnpmLock and commit the result");
        if (Files.exists(lock)) Files.copy(lock, installedLock, StandardCopyOption.REPLACE_EXISTING);
        else Files.deleteIfExists(installedLock);
        if (command.equals("ci")) verifyLock(lock);
        final var toolchain = JnpmFiles.read(getToolchainFile().get().getAsFile().toPath());
        final var arguments = new ArrayList<>(List.of(toolchain.get("node").getAsString(), toolchain.get("npm").getAsString(), command,
                "--ignore-scripts", "--no-audit", "--no-fund", "--include=dev", "--strict-peer-deps",
                "--cache", getCacheDirectory().get().getAsFile().getAbsolutePath()));
        if (!command.equals("ci")) arguments.add("--package-lock-only");
        if (getOffline().get()) arguments.add("--offline");
        final var emptyConfig = workspace.resolve("jnpm/empty-npmrc");
        final var globalConfig = workspace.resolve("jnpm/empty-global-npmrc");
        JnpmFiles.writeText(emptyConfig, "");
        JnpmFiles.writeText(globalConfig, "");
        arguments.addAll(List.of("--userconfig", emptyConfig.toString(), "--globalconfig", globalConfig.toString()));
        JnpmProcess.run(arguments, workspace, getTokens().get(), getLogger());
        if (!command.equals("ci")) {
            // Only explicit lock/update tasks write project source state, and only after npm succeeds.
            JnpmFiles.writeText(lock, Files.readString(installedLock));
        }
    }

    private void verifyLock(Path lock) throws IOException {
        final var manifest = JnpmFiles.read(getManifestFile().get().getAsFile().toPath());
        final var locked = JnpmFiles.read(lock);
        if (!locked.has("packages") || !locked.getAsJsonObject("packages").has("")) throw new GradleException("Invalid jnpm.lock; run jnpmLock");
        final var root = locked.getAsJsonObject("packages").getAsJsonObject("");
        for (final var key : List.of("dependencies", "devDependencies")) {
            final var expected = manifest.getAsJsonObject(key);
            final var actual = root.has(key) ? root.getAsJsonObject(key) : new com.google.gson.JsonObject();
            if (!expected.equals(actual)) throw new GradleException("JNPM declarations differ from jnpm.lock; run jnpmLock and commit the result");
        }
    }
}
