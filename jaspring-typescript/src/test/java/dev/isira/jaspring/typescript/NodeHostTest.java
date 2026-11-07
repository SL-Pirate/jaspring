package dev.isira.jaspring.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.typescript.internal.NodeLocator;

/// Skipped without Node on the PATH; CI fails when it was skipped.
class NodeHostTest {

    @Test
    void nodeHostProducesIdenticalResults() {
        assumeTrue(NodeLocator.find().isPresent(), "Node 20+ is not on the PATH");
        final var files = List.of(new SourceFile("sparkline.ts", TypescriptCompilerTest.SPARKLINE),
                new SourceFile("bad.ts", "export const n: number = window.nope;\n"));
        final CompileResult graal;
        try (var compiler = TypescriptCompiler.load(Host.GRAALJS)) {
            graal = compiler.compile(CompileRequest.of(files));
        }
        try (var compiler = TypescriptCompiler.load(Host.NODE)) {
            assertEquals(Host.NODE, compiler.host());
            assertFalse(compiler.interpreted());
            assertEquals(System.getProperty("jaspring.versions.typescript"), compiler.version());
            final var node = compiler.compile(CompileRequest.of(files));
            assertEquals(graal, node);
            assertEquals(graal.outputs().getFirst(), compiler.transpile(files.getFirst()).outputs().getFirst());
        }
    }
}
