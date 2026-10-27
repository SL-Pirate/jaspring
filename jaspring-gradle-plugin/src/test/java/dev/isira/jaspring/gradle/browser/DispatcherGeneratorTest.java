package dev.isira.jaspring.gradle.browser;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DispatcherGeneratorTest {

    @TempDir
    Path temp;

    @Test
    void generatesOneCasePerClosureWithDecodersAndEncoders() throws Exception {
        final var classes = temp.resolve("classes");
        Fixtures.compile(classes, Map.of("fx.Comp", Fixtures.COMPONENT));
        final var closures = ClosureScanner.scan(List.of(classes), List.of(Fixtures.CORE_JAR));

        final var source = DispatcherGenerator.generate(closures);

        assertTrue(source.contains("public static void main(String[] args)"), source);
        assertTrue(source.contains("WasmBridge.install(JaspringWasmEntry::run)"), source);
        for (final var closure : closures) {
            assertTrue(source.contains("case \"" + closure.id() + "\""), closure.id());
            assertTrue(source.contains("fx.Comp." + closure.implMethod() + "("), closure.implMethod());
        }
        assertTrue(source.contains("Decoders.string(args, \"0\")"), "captured String decoded");
        assertTrue(source.contains("Decoders.integer(args, \"1\")"), "captured int decoded");
        assertTrue(source.contains("fx.Comp.Kind.valueOf("), "enum decoded by name");
        assertTrue(source.contains("static JSObject encode"), "record encoder generated");
        assertTrue(source.contains("(fx.Comp.Spot r)"), "record result encoded");
        assertTrue(source.contains("Results.promise("), "async closure chained");
        assertTrue(source.contains("Results.error(\"unknown closure \""), source);
    }

    @Test
    void generatedSourceCompilesAgainstTheRuntimeOnGraalvm() throws Exception {
        assumeTrue(Fixtures.graalvm(), "needs the org.graalvm.webimage.api module of Oracle GraalVM");
        final var runtimeJar = Path.of(System.getProperty("jaspring.browserRuntimeJar"));
        assumeTrue(Files.isRegularFile(runtimeJar), "jaspring-browser-runtime jar is built");
        final var classes = temp.resolve("classes");
        final var rewritten = temp.resolve("rewritten");
        Fixtures.compile(classes, Map.of("fx.Comp", Fixtures.COMPONENT));
        final var closures = ClosureScanner.scan(List.of(classes), List.of(Fixtures.CORE_JAR));
        ClosureRewriter.rewrite(closures, new TypeShapes(List.of(classes), List.of(Fixtures.CORE_JAR)), rewritten);

        final var dispatcher = temp.resolve("dispatcher");
        Fixtures.compile(dispatcher, Map.of(DispatcherGenerator.MAIN_CLASS, DispatcherGenerator.generate(closures)), rewritten, runtimeJar);

        assertTrue(Files.exists(dispatcher.resolve("dev/isira/jaspring/browser/generated/JaspringWasmEntry.class")));
    }
}
