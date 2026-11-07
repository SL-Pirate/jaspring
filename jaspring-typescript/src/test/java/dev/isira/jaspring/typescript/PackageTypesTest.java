package dev.isira.jaspring.typescript;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PackageTypesTest {
    private static TypescriptCompiler compiler;
    @BeforeAll static void load() { compiler = TypescriptCompiler.load(); }
    @AfterAll static void close() { compiler.close(); }

    private static DependencySnapshot packages() {
        return new DependencySnapshot(Map.of(
                "node_modules/example/package.json", """
                        {"name":"example","version":"1.0.0","exports":{
                          ".":{"types":"./types/index.d.ts","browser":"./browser.js","default":"./node.js"},
                          "./extra":{"types":"./types/extra.d.ts","default":"./extra.js"}}}
                        """,
                "node_modules/example/types/index.d.ts", "export declare const value: number;",
                "node_modules/example/types/extra.d.ts", "export declare const extra: string;",
                "node_modules/example/types/private.d.ts", "export declare const hidden: string;",
                "node_modules/dev-only/package.json", "{\"name\":\"dev-only\",\"version\":\"1.0.0\",\"types\":\"index.d.ts\"}",
                "node_modules/dev-only/index.d.ts", "export declare const dev: number;"), List.of(), List.of("example"));
    }

    private static CompileRequest request(String source) {
        return new CompileRequest(List.of(new SourceFile("page.ts", source)), CompileOptions.defaults(), packages());
    }

    @Test void resolvesPackageExportsAndChecksTheirTypesWithoutEmitting() {
        final var result = compiler.check(request("import { value } from 'example'; import { extra } from 'example/extra'; const n: number = value; const s: string = extra;"));
        assertFalse(result.hasErrors(), result.diagnostics().toString());
        assertTrue(result.outputs().isEmpty());
        assertTrue(compiler.check(request("import { value } from 'example'; const text: string = value;")).hasErrors());
    }

    @Test void doesNotExposePrivateExportsOrDevelopmentPackages() {
        assertTrue(compiler.check(request("import { hidden } from 'example/types/private'; console.log(hidden);")).hasErrors());
        assertTrue(compiler.check(request("import { dev } from 'dev-only'; console.log(dev);")).hasErrors());
    }

    @Test void doesNotLeakNodeGlobalsIntoBrowserPrograms() {
        assertTrue(compiler.check(request("process.exit(1);")).hasErrors());
        assertTrue(compiler.check(request("import fs from 'node:fs'; fs.readFileSync('x');")).hasErrors());
    }

    @Test void snapshotsCannotEscapeTheirVirtualRoot() {
        assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(Map.of("../secret", "x"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(Map.of(), List.of("missing.d.ts"), List.of()));
    }

    @Test void transpileOnlyCheckingDoesNotPublishIntermediateFiles() {
        final var request = new CompileRequest(List.of(new SourceFile("page.ts", "const n: number = 'x';")), CompileOptions.transpileOnly(), packages());
        final var result = compiler.check(request);
        assertFalse(result.hasErrors());
        assertTrue(result.outputs().isEmpty());
    }
}
