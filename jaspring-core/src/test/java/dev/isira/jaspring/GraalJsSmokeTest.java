package dev.isira.jaspring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

class GraalJsSmokeTest {

    @Test
    void evaluatesEsModuleAndReturnsItsExports() throws Exception {
        try (var context = Context.newBuilder("js")
                .option("js.esm-eval-returns-exports", "true")
                .build()) {
            final var source = Source.newBuilder("js", "export const answer = 6 * 7;", "smoke.mjs")
                    .mimeType("application/javascript+module")
                    .build();

            final var exports = context.eval(source);

            assertEquals(42, exports.getMember("answer").asInt());
        }
    }

    @Test
    void usesOptimizingRuntimeOnGraalVm() {
        assumeTrue(System.getProperty("java.vendor.version", "").contains("GraalVM"),
                "optimizing runtime is only expected on a GraalVM JDK");

        try (var engine = Engine.create()) {
            assertNotEquals("Interpreted", engine.getImplementationName(), () ->
                    "Expected an optimizing GraalJS runtime. Java: %s; GraalVM: %s; Polyglot: %s. "
                            .formatted(System.getProperty("java.runtime.version"),
                                    System.getProperty("java.vendor.version"), engine.getVersion())
                            + "Use a GraalVM compiler release compatible with the Polyglot dependency.");
        }
    }
}
