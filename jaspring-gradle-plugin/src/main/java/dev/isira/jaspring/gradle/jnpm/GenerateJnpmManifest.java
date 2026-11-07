package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

@CacheableTask
public abstract class GenerateJnpmManifest extends DefaultTask {
    @Input public abstract MapProperty<String, String> getImplementation();
    @Input public abstract MapProperty<String, String> getApi();
    @Input public abstract MapProperty<String, String> getDevImplementation();
    @Input public abstract MapProperty<String, String> getDevApi();
    @Input public abstract Property<Boolean> getLintEnabled();
    @Input public abstract MapProperty<String, String> getRegistries();
    @Input public Map<String, String> getTools() { return JnpmFiles.toolDependencies(getLintEnabled().get()); }
    @Input public abstract Property<String> getSourcePath();
    @Input public String getProjectPath() {
        return getManifestFile().get().getAsFile().toPath().getParent().relativize(getProjectDirectory().get().getAsFile().toPath())
                .toString().replace(java.io.File.separatorChar, '/');
    }
    @Internal public abstract DirectoryProperty getProjectDirectory();
    @OutputFile public abstract RegularFileProperty getManifestFile();
    @OutputFile public abstract RegularFileProperty getConfigFile();
    @OutputFile public abstract RegularFileProperty getNpmrcFile();

    @TaskAction
    public void generate() throws IOException {
        final var scopes = new LinkedHashMap<String, Map<String, String>>();
        scopes.put("implementation", new TreeMap<>(getImplementation().get()));
        scopes.put("api", new TreeMap<>(getApi().get()));
        scopes.put("devImplementation", new TreeMap<>(getDevImplementation().get()));
        scopes.put("devApi", new TreeMap<>(getDevApi().get()));
        final var names = new HashSet<String>();
        for (final var scope : scopes.values()) {
            for (final var name : scope.keySet()) if (!names.add(name)) throw new GradleException("Declare " + name + " in only one JNPM scope");
        }
        final var production = new TreeMap<>(scopes.get("implementation"));
        production.putAll(scopes.get("api"));
        final var development = new TreeMap<>(getTools());
        development.putAll(scopes.get("devImplementation"));
        development.putAll(scopes.get("devApi"));
        for (final var tool : getTools().entrySet()) {
            if (production.containsKey(tool.getKey()) || !tool.getValue().equals(development.get(tool.getKey()))) {
                throw new GradleException("JNPM manages " + tool.getKey() + " at " + tool.getValue() + "; remove the conflicting declaration");
            }
        }
        JnpmFiles.write(getManifestFile().get().getAsFile().toPath(), Map.of("name", "jaspring-jnpm", "version", "0.0.0", "private", true,
                "type", "module", "dependencies", production, "devDependencies", development));
        // Relative paths keep Gradle outputs relocatable; bridge users resolve these against this config's directory.
        JnpmFiles.write(getConfigFile().get().getAsFile().toPath(), Map.of("version", 1, "scopes", scopes,
                "lint", getLintEnabled().get(), "source", getSourcePath().get(), "tools", getTools(),
                "project", getProjectPath()));
        final var npmrc = new StringBuilder("registry=https://registry.npmjs.org/\nignore-scripts=true\nstrict-peer-deps=true\naudit=false\nfund=false\n");
        final var seenScopes = new HashSet<String>();
        for (final var entry : new TreeMap<>(getRegistries().get()).entrySet()) {
            final var fields = entry.getValue().split("\\n", -1);
            final var uri = URI.create(fields[0]);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getScheme().equals("https") || uri.getScheme().equals("http") && List.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost()))) {
                throw new GradleException("JNPM registry URLs must use HTTPS (HTTP is allowed for local registry tests) and contain no credentials");
            }
            final var url = uri.toString().endsWith("/") ? uri.toString() : uri + "/";
            final var scopesForRegistry = fields[1].isEmpty() ? List.of("") : List.of(fields[1].split(","));
            for (final var scope : scopesForRegistry) {
                if (!scope.isEmpty() && !scope.matches("@[a-z0-9._-]+")) throw new GradleException("Invalid registry scope: " + scope);
                if (!seenScopes.add(scope)) throw new GradleException("Multiple JNPM registries claim scope '" + scope + "'");
                npmrc.append(scope.isEmpty() ? "registry" : scope + ":registry").append('=').append(url).append('\n');
            }
            if (fields.length > 2 && fields[2].equals("true")) {
                npmrc.append(url.substring(url.indexOf(':') + 1)).append(":_authToken=${JNPM_TOKEN_").append(entry.getKey()).append("}\n");
            }
        }
        JnpmFiles.writeText(getNpmrcFile().get().getAsFile().toPath(), npmrc.toString());
    }
}
