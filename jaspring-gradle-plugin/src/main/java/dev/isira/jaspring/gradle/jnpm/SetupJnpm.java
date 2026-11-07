package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.nio.file.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Refers to a local managed toolchain outside the project")
public abstract class SetupJnpm extends DefaultTask {
    @Input public String getToolchainVersion() { return JnpmFiles.VERSIONS.getProperty("node"); }
    @Internal public abstract DirectoryProperty getNodeHome();
    @Internal public abstract DirectoryProperty getCacheDir();
    @Internal public abstract Property<Boolean> getOffline();
    @OutputFile public abstract RegularFileProperty getToolchainFile();

    @TaskAction
    public void setup() throws IOException {
        final var home = getNodeHome().isPresent() ? getNodeHome().get().getAsFile().toPath()
                : JnpmToolchain.install(getCacheDir().get().getAsFile().toPath(), getOffline().get());
        if (!Files.isExecutable(JnpmToolchain.node(home)) || !Files.isRegularFile(JnpmToolchain.npm(home))) {
            throw new GradleException("jnpm.nodeHome must contain Node 22.15+ (22.x) or Node 24+, and npm: " + home);
        }
        JnpmProcess.run(java.util.List.of(JnpmToolchain.node(home).toString(), "--input-type=module", "-e",
                "const [a,b]=process.versions.node.split('.').map(Number);if(!((a===22&&b>=15)||a>=24))throw Error('JNPM requires Node 22.15+ (22.x) or Node 24+')"),
                home, java.util.Map.of(), getLogger());
        JnpmFiles.write(getToolchainFile().get().getAsFile().toPath(), java.util.Map.of(
                "node", JnpmToolchain.node(home).toAbsolutePath().toString(), "npm", JnpmToolchain.npm(home).toAbsolutePath().toString()));
    }
}
