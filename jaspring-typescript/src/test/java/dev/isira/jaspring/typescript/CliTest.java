package dev.isira.jaspring.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    @TempDir
    Path dir;

    @Test
    void writesOutputsManifestAndDiagnostics() throws IOException {
        final var src = Files.createDirectories(dir.resolve("src"));
        Files.createDirectories(src.resolve("lib"));
        Files.writeString(src.resolve("lib/util.ts"), "export const one = 1;\n");
        Files.writeString(src.resolve("page.ts"), "import { one } from './lib/util.js';\nconsole.log(one);\n");
        Files.writeString(src.resolve("globals.d.ts"), "declare const VERSION: string;\n");

        assertEquals(0, Cli.run(args(src, false)));

        assertTrue(Files.exists(dir.resolve("out/lib/util.js")));
        assertTrue(Files.readString(dir.resolve("out/page.js")).contains("from './lib/util.js'"));
        assertFalse(Files.exists(dir.resolve("out/globals.js")));
        final var manifest = Files.readString(dir.resolve("manifest/jaspring-typescript.json"));
        assertTrue(manifest.startsWith("{\"version\":1,\"typescript\":\"" + System.getProperty("jaspring.versions.typescript") + "\",\"files\":["), manifest);
        assertTrue(manifest.contains("{\"source\":\"globals.d.ts\",\"sha256\":\""), manifest);
        assertTrue(manifest.contains("\"output\":null}"), manifest);
        assertTrue(manifest.contains("{\"source\":\"lib/util.ts\",\"sha256\":\""), manifest);
        assertTrue(manifest.contains("\"output\":\"lib/util.js\"}"), manifest);
        assertTrue(manifest.contains("\"output\":\"page.js\"}"), manifest);
        assertEquals("", Files.readString(dir.resolve("diagnostics.txt")));
    }

    @Test
    void typeErrorsExitWithOneAndTranspileOnlyIgnoresThem() throws IOException {
        final var src = Files.createDirectories(dir.resolve("src"));
        Files.writeString(src.resolve("bad.ts"), "export const n: number = 'x';\n");

        assertEquals(1, Cli.run(args(src, false)));
        assertEquals("bad.ts(1,14): error TS2322: Type 'string' is not assignable to type 'number'.\n", Files.readString(dir.resolve("diagnostics.txt")));

        assertEquals(0, Cli.run(args(src, true)));
        assertEquals("", Files.readString(dir.resolve("diagnostics.txt")));
        assertTrue(Files.exists(dir.resolve("out/bad.js")));
    }

    @Test
    void badArgumentsExitWithTwo() {
        assertEquals(2, Cli.run(new String[] {"--source-dir", dir.toString()}));
        assertEquals(2, Cli.run(new String[] {"--bogus"}));
        assertEquals(2, Cli.run(args(dir.resolve("missing"), false)));
    }

    private String[] args(Path src, boolean noTypeCheck) {
        final var base = new String[] {"--source-dir", src.toString(), "--out", dir.resolve("out").toString(),
                "--manifest", dir.resolve("manifest/jaspring-typescript.json").toString(), "--diagnostics", dir.resolve("diagnostics.txt").toString()};
        if (!noTypeCheck) return base;
        final var args = java.util.Arrays.copyOf(base, base.length + 1);
        args[base.length] = "--no-type-check";
        return args;
    }
}
