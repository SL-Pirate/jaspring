package dev.isira.jaspring.boot.typescript;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.isira.jaspring.boot.JaspringProperties;

class TypescriptBundleManifestTest {
    @TempDir Path project;

    @Test void bundledEntriesAreExplicitAndStylesAreSeparate() {
        final var manifest = TypescriptManifestLoader.parse("""
                {"version":2,"typescript":"6.0.3","bundled":true,"lockSha256":"lock",
                 "files":[{"source":"page.ts","sha256":"source","output":"entry/page-HASH.js"},
                          {"source":"helper.ts","sha256":"helper","output":null}],
                 "entries":["entry/page-HASH.js"],"styles":["page-HASH.css"],"inputs":{"page.ts":"source"}}
                """);
        assertTrue(manifest.bundled());
        assertEquals(List.of("entry/page-HASH.js"), manifest.entryPoints().stream().map(TypescriptScripts.Script::output).toList());
        assertEquals(List.of("page-HASH.css"), manifest.stylesheets());
        assertEquals("lock", manifest.lockSha256());
    }

    @Test void olderManifestsKeepTheirEntrypointConvention() {
        final var manifest = TypescriptManifestLoader.parse("""
                {"version":1,"files":[{"source":"page.ts","sha256":"digest","output":"page.js"},
                                      {"source":"lib/util.ts","sha256":"digest","output":"lib/util.js"}]}
                """);
        assertFalse(manifest.bundled());
        assertEquals(1, manifest.entryPoints().size());
        assertTrue(manifest.stylesheets().isEmpty());
    }

    @Test void invalidManifestVersionsAndPathsFailClearly() {
        assertThrows(IllegalArgumentException.class, () -> TypescriptManifestLoader.parse("{\"version\":3}"));
        assertThrows(IllegalArgumentException.class, () -> TypescriptManifestLoader.parse("{\"version\":2,\"styles\":[\"../secret\"]}"));
    }

    @Test void startupDetectsChangedAssetsAndLocksWithoutRebuildingPackages() throws Exception {
        final var source = Files.createDirectory(project.resolve("src"));
        final var lock = Files.writeString(project.resolve("jnpm.lock"), "locked");
        Files.writeString(source.resolve("page.ts"), "console.log(1);");
        Files.writeString(source.resolve("style.css"), "body {}");
        final var digests = Map.of("page.ts", digest("console.log(1);"), "style.css", digest("body {}"));
        final var manifest = new TypescriptScripts("6.0.3", List.of(), true, List.of(), List.of(), digest("locked"), digests);
        assertTrue(TypescriptStartupCompiler.bundleInputsMatch(source, lock, manifest));
        Files.writeString(source.resolve("style.css"), "body {color:red}");
        assertFalse(TypescriptStartupCompiler.bundleInputsMatch(source, lock, manifest));
        Files.writeString(source.resolve("style.css"), "body {}");
        Files.writeString(lock, "updated");
        assertFalse(TypescriptStartupCompiler.bundleInputsMatch(source, lock, manifest));
        Files.delete(lock);
        assertFalse(TypescriptStartupCompiler.bundleInputsMatch(source, lock, manifest));
        assertSame(manifest, TypescriptStartupCompiler.compileIfStale(new JaspringProperties.Typescript(), manifest, List.of()));
        final var failed = assertThrows(TypescriptCompileException.class, () -> TypescriptStartupCompiler.compileIfStale(
                new JaspringProperties.Typescript(), manifest, List.of(new TypescriptSources.Source("page.ts", "changed".getBytes()))));
        assertTrue(failed.getMessage().contains("compileTypescript"));
    }

    private static String digest(String text) {
        return new TypescriptSources.Source("", text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).sha256();
    }
}
