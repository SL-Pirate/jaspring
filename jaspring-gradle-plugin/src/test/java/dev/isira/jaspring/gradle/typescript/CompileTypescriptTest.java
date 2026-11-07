package dev.isira.jaspring.gradle.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The TypeScript build in a consumer project, with the compiler jars from `jaspring.typescriptClasspath`.
class CompileTypescriptTest {

    static final String HELLO = """
            jaspring.hooks.register<HTMLElement, string>('hello', {
              mounted(el, ctx) { el.textContent = ctx.params; },
            });
            """;

    @TempDir
    Path project;

    @BeforeEach
    void setup() throws IOException {
        write("settings.gradle", "rootProject.name = 'consumer'\n");
        final var jars = Arrays.stream(System.getProperty("jaspring.typescriptClasspath").split(File.pathSeparator))
                .map(path -> "'" + escape(path) + "'").collect(Collectors.joining(", "));
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'dev.isira.jaspring'
                }
                dependencies {
                    implementation files('%s')
                    jaspringTypescript files(%s)
                }
                """.formatted(escape(System.getProperty("jaspring.coreJar")), jars));
        write("src/main/typescript/hello.ts", HELLO);
    }

    @Test
    void compilesToResourcesWithManifestAndTypes() throws IOException {
        final var result = runner("build").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileTypescript").getOutcome(), result.getOutput());
        final var js = Files.readString(project.resolve("build/resources/main/jaspring/ts/hello.js"));
        assertTrue(js.contains("jaspring.hooks.register('hello', {"), js);
        assertFalse(js.contains("HTMLElement"), js);
        final var manifest = Files.readString(project.resolve("build/resources/main/jaspring/jaspring-typescript.json"));
        assertTrue(manifest.contains("\"source\":\"hello.ts\""), manifest);
        assertTrue(manifest.contains("\"output\":\"hello.js\""), manifest);
        assertTrue(manifest.matches("(?s).*\"sha256\":\"[0-9a-f]{64}\".*"), manifest);
        assertTrue(Files.readString(project.resolve("build/jaspring/typescript/types/jaspring.d.ts")).contains("declare var jaspring"));
        assertTrue(Files.readString(project.resolve("build/jaspring/typescript/types/tsconfig.base.json")).contains("\"strict\": true"));
        assertTrue(result.getOutput().contains(":compileTypescript: 1 file(s) compiled from src/main/typescript"), result.getOutput());

        assertEquals(TaskOutcome.UP_TO_DATE, runner("build").build().task(":compileTypescript").getOutcome());
    }

    @Test
    void nestedModulesKeepTheirRelativePaths() throws IOException {
        write("src/main/typescript/lib/util.ts", "export const one = 1;\n");
        write("src/main/typescript/page.ts", "import { one } from './lib/util.js';\nconsole.log(one);\n");
        runner("compileTypescript").build();
        assertTrue(Files.exists(project.resolve("build/jaspring/typescript/js/lib/util.js")));
        assertTrue(Files.readString(project.resolve("build/jaspring/typescript/js/page.js")).contains("from './lib/util.js'"));
    }

    @Test
    void typeErrorsFailWithTscStyleDiagnostics() throws IOException {
        write("src/main/typescript/hello.ts", "const n: number = 'x';\n");
        final var result = runner("build").buildAndFail();
        final var output = result.getOutput();
        assertEquals(TaskOutcome.FAILED, result.task(":compileTypescript").getOutcome());
        assertTrue(output.contains("src/main/typescript/hello.ts(1,7): error TS2322: Type 'string' is not assignable to type 'number'."), output);
        assertTrue(output.contains("TypeScript: 1 error(s) in src/main/typescript"), output);
        assertTrue(output.contains("jaspring.typescript.typeCheck=false"), output);
        assertFalse(Files.exists(project.resolve("build/resources/main/jaspring/ts/hello.js")));
    }

    @Test
    void transpileOnlyIgnoresTypeErrors() throws IOException {
        write("src/main/typescript/hello.ts", "const n: number = 'x';\nconsole.log(n);\n");
        final var result = runner("build", "-Pjaspring.typescript.typeCheck=false").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileTypescript").getOutcome());
        assertEquals("const n = 'x';\nconsole.log(n);\nexport {};\n", Files.readString(project.resolve("build/resources/main/jaspring/ts/hello.js")));
    }

    @Test
    void projectsWithoutTypescriptSkipTheTask() throws IOException {
        Files.delete(project.resolve("src/main/typescript/hello.ts"));
        final var result = runner("build").build();
        assertEquals(TaskOutcome.NO_SOURCE, result.task(":compileTypescript").getOutcome());
        assertFalse(Files.exists(project.resolve("build/resources/main/jaspring/jaspring-typescript.json")));
    }

    @Test
    void skipPropertyWarns() {
        final var result = runner("build", "-Pjaspring.typescript.skip=true").build();
        assertEquals(TaskOutcome.SKIPPED, result.task(":compileTypescript").getOutcome());
        assertTrue(result.getOutput().contains("jaspring.typescript.skip is set"), result.getOutput());
        assertFalse(Files.exists(project.resolve("build/resources/main/jaspring/ts/hello.js")));
    }

    @Test
    void nodeHostProducesIdenticalOutput() throws IOException {
        assumeTrue(nodeOnPath(), "Node 20+ is not on the PATH");
        runner("compileTypescript").build();
        final var graal = Files.readString(project.resolve("build/jaspring/typescript/js/hello.js"));
        final var result = runner("compileTypescript", "--rerun-tasks", "-Pjaspring.typescript.host=node").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileTypescript").getOutcome(), result.getOutput());
        assertEquals(graal, Files.readString(project.resolve("build/jaspring/typescript/js/hello.js")));
    }

    private static boolean nodeOnPath() {
        final var path = System.getenv("PATH");
        return path != null && Arrays.stream(path.split(File.pathSeparator)).map(dir -> new File(dir, "node")).anyMatch(File::canExecute);
    }

    private GradleRunner runner(String... arguments) {
        final var all = new ArrayList<>(List.of(arguments));
        all.add("--stacktrace");
        all.add("--offline");
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(all);
    }

    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void write(String path, String content) throws IOException {
        final var target = project.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
