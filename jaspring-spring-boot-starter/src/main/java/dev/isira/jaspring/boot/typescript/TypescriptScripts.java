package dev.isira.jaspring.boot.typescript;

import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;

/// The application's compiled TypeScript: what the Gradle plugin's `compileTypescript` task recorded in
/// `jaspring/jaspring-typescript.json`, or what the startup fallback compiled when that manifest was missing or
/// stale. Served under [#URL_PREFIX]; top-level files are entry points and get a `<script type="module">` tag,
/// files in subdirectories are modules reached through imports.
public record TypescriptScripts(String typescript, List<Script> scripts) {

    public static final String URL_PREFIX = "/_jaspring/ts/";

    public static final TypescriptScripts NONE = new TypescriptScripts(null, List.of());

    public TypescriptScripts {
        scripts = List.copyOf(scripts);
    }

    /// `output` is null for declaration files; `resource` is null when the file is on the classpath under
    /// `jaspring/ts/` and holds the compiled text when the startup fallback produced it.
    public record Script(String source, String sha256, String output, Resource resource) {}

    public boolean isEmpty() {
        return scripts.isEmpty();
    }

    public List<Script> entryPoints() {
        return scripts.stream().filter(script -> script.output() != null && script.output().indexOf('/') < 0).toList();
    }

    public Optional<Resource> inMemory(String output) {
        return scripts.stream().filter(script -> script.resource() != null && output.equals(script.output())).map(Script::resource).findFirst();
    }
}
