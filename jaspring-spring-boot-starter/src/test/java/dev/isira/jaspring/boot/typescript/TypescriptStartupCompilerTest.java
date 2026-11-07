package dev.isira.jaspring.boot.typescript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class TypescriptStartupCompilerTest {

    private static TypescriptSources.Source source(String path, String text) {
        return new TypescriptSources.Source(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static TypescriptScripts manifest(TypescriptSources.Source... sources) {
        return new TypescriptScripts("6.0.3", java.util.Arrays.stream(sources)
                .map(s -> new TypescriptScripts.Script(s.path(), s.sha256(), s.path().endsWith(".d.ts") ? null : s.path().replace(".ts", ".js"), null)).toList());
    }

    @Test
    void packagedApplicationsTrustTheManifest() {
        assertFalse(TypescriptStartupCompiler.stale(manifest(source("a.ts", "1")), List.of()));
        assertFalse(TypescriptStartupCompiler.stale(TypescriptScripts.NONE, List.of()));
    }

    @Test
    void missingManifestIsStaleWhenSourcesExist() {
        assertTrue(TypescriptStartupCompiler.stale(TypescriptScripts.NONE, List.of(source("a.ts", "1"))));
    }

    @Test
    void matchingDigestsAreFresh() {
        final var a = source("a.ts", "1");
        final var types = source("types/globals.d.ts", "declare const X: 1;");
        assertFalse(TypescriptStartupCompiler.stale(manifest(a, types), List.of(a, types)));
    }

    @Test
    void changedAddedOrRemovedSourcesAreStale() {
        final var a = source("a.ts", "1");
        final var b = source("b.ts", "2");
        assertTrue(TypescriptStartupCompiler.stale(manifest(a), List.of(source("a.ts", "changed"))));
        assertTrue(TypescriptStartupCompiler.stale(manifest(a), List.of(a, b)));
        assertTrue(TypescriptStartupCompiler.stale(manifest(a, b), List.of(a)));
        assertTrue(TypescriptStartupCompiler.stale(manifest(a), List.of(source("renamed.ts", "1"))));
    }
}
