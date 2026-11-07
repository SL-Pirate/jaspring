package dev.isira.jaspring.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.isira.jaspring.typescript.internal.Json;

class TypescriptCompilerTest {

    static final String SPARKLINE = """
            function draw(el: HTMLCanvasElement, points: number[]): void {
              const context = el.getContext('2d');
              if (!context) return;
              context.fillRect(0, 0, el.width, Math.max(...points, 1));
            }
            jaspring.hooks.register<HTMLCanvasElement, number[]>('sparkline', {
              mounted(el, ctx) { draw(el, ctx.params); ctx.pushEvent('point-picked', { index: 2 }); },
              updated(el, ctx) { draw(el, ctx.params); },
              size(el) { return { width: el.width, height: el.height }; },
            });
            jaspring.calls.register('viewport', () => ({ width: window.innerWidth }));
            """;

    private static TypescriptCompiler compiler;

    @BeforeAll
    static void load() {
        compiler = TypescriptCompiler.load();
    }

    @AfterAll
    static void close() {
        compiler.close();
    }

    @Test
    void bundleVersionMatchesTheVersionCatalog() {
        assertEquals(System.getProperty("jaspring.versions.typescript"), compiler.version());
    }

    @Test
    void transpileEmitsAnEs2022Module() {
        final var result = compiler.transpile(new SourceFile("hooks/hello.ts", "const n: number = 1;\nconsole.log(n);\n"));
        assertEquals(List.of(), result.diagnostics());
        final var output = result.output("hooks/hello.js").orElseThrow();
        assertEquals("const n = 1;\nconsole.log(n);\nexport {};\n", output.text());
    }

    @Test
    void typeErrorCarriesLineColumnAndCode() {
        final var result = compiler.compile(CompileRequest.of(List.of(new SourceFile("bad.ts", "export const n: number = 'x';\n"))));
        assertTrue(result.hasErrors());
        final var error = result.errors().getFirst();
        assertEquals("bad.ts", error.file());
        assertEquals(1, error.line());
        assertEquals(14, error.column());
        assertEquals(2322, error.code());
        assertEquals("bad.ts(1,14): error TS2322: Type 'string' is not assignable to type 'number'.", error.format());
        assertEquals("src/main/typescript/bad.ts(1,14): error TS2322: Type 'string' is not assignable to type 'number'.",
                error.format("src/main/typescript/"));
        assertTrue(result.output("bad.js").isPresent(), "emits despite the error");
    }

    @Test
    void jaspringGlobalIsTyped() {
        final var result = compiler.compile(CompileRequest.of(List.of(new SourceFile("sparkline.ts", SPARKLINE),
                new SourceFile("dom.ts", "export const nope = window.nope;\n"))));
        assertEquals(1, result.diagnostics().size(), result.diagnostics().toString());
        final var error = result.errors().getFirst();
        assertEquals("dom.ts", error.file());
        assertEquals(2339, error.code());
        assertTrue(error.message().contains("'nope' does not exist"), error.message());
        assertTrue(result.output("sparkline.js").orElseThrow().text().contains("jaspring.hooks.register('sparkline', {"));
    }

    @Test
    void relativeImportsResolveJsToTsAndRewriteTs() {
        final var result = compiler.compile(CompileRequest.of(List.of(
                new SourceFile("lib/util.ts", "export function clamp(value: number): number { return value; }\n"),
                new SourceFile("page.ts", "import { clamp } from './lib/util.js';\nimport { clamp as c2 } from './lib/util.ts';\nconsole.log(clamp(1), c2(2));\n"))));
        assertEquals(List.of(), result.diagnostics());
        final var page = result.output("page.js").orElseThrow().text();
        assertTrue(page.contains("from './lib/util.js'"), page);
        assertTrue(page.contains("from \"./lib/util.js\""), page);
        assertFalse(page.contains(".ts"), page);
        assertTrue(result.output("lib/util.js").isPresent());
    }

    @Test
    void transpileMatchesProgramEmit() {
        final var files = List.of(new SourceFile("sparkline.ts", SPARKLINE),
                new SourceFile("lib/util.ts", "import type { Hook } from 'jaspring';\nexport const hook: Hook = {};\nexport class Box { value = 1; }\n"));
        final var checked = compiler.compile(new CompileRequest(files, CompileOptions.defaults()));
        final var transpiled = compiler.compile(new CompileRequest(files, CompileOptions.transpileOnly()));
        assertEquals(List.of(), checked.diagnostics());
        assertEquals(List.of(), transpiled.diagnostics());
        assertEquals(checked.outputs(), transpiled.outputs());
        for (final var file : files) {
            assertEquals(checked.output(file.outputPath()).orElseThrow().text(), compiler.transpile(file).outputs().getFirst().text());
        }
    }

    @Test
    void transpileOnlyStillReportsSyntaxErrors() {
        final var result = compiler.compile(new CompileRequest(List.of(new SourceFile("broken.ts", "const = ;\n")), CompileOptions.transpileOnly()));
        assertTrue(result.hasErrors());
        assertEquals("broken.ts", result.errors().getFirst().file());
    }

    @Test
    void sourceMapsAreEmittedOnRequest() {
        final var result = compiler.compile(new CompileRequest(List.of(new SourceFile("a.ts", "export const a = 1;\n")), new CompileOptions(true, true)));
        assertTrue(result.output("a.js.map").isPresent());
        assertTrue(result.output("a.js").orElseThrow().text().contains("//# sourceMappingURL=a.js.map"));
    }

    @Test
    void optionsMatchTheShippedTsconfig() {
        @SuppressWarnings("unchecked")
        final var shipped = (Map<String, Object>) ((Map<String, Object>) Json.parse(TypescriptCompiler.baseTsconfig())).get("compilerOptions");
        shipped.remove("noEmit");
        assertEquals(compiler.options(), shipped);
    }

    @Test
    void rejectsPathsOutsideTheRoot() {
        assertThrows(IllegalArgumentException.class, () -> new SourceFile("../x.ts", ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceFile("/x.ts", ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceFile("a//x.ts", ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceFile("x.tsx", ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceFile("x.mts", ""));
        assertThrows(IllegalArgumentException.class, () -> new CompileRequest(List.of(new SourceFile("x.ts", ""), new SourceFile("x.ts", "")), CompileOptions.defaults()));
        assertEquals("a/b.js", new SourceFile("a/b.ts", "").outputPath());
        assertEquals(null, new SourceFile("a/b.d.ts", "").outputPath());
    }

    @Test
    void jaspringTypesAreAvailableForEditors() {
        assertTrue(TypescriptCompiler.jaspringTypes().contains("declare var jaspring: JaspringGlobal;"));
    }
}
