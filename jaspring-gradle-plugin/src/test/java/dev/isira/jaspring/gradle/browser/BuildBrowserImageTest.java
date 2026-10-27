package dev.isira.jaspring.gradle.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The WebAssembly build in a consumer project. The end-to-end build needs Oracle GraalVM and Binaryen and runs
/// only with `-Pjaspring.wasm=true`; the skip and failure paths run everywhere.
class BuildBrowserImageTest {

    @TempDir
    Path project;

    @BeforeEach
    void setup() throws IOException {
        write("settings.gradle", "rootProject.name = 'consumer'\n");
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'dev.isira.jaspring'
                }
                dependencies {
                    implementation files('%s')
                    jaspringBrowser files('%s')
                }
                """.formatted(escape(System.getProperty("jaspring.coreJar")), escape(System.getProperty("jaspring.browserRuntimeJar"))));
        write("src/main/java/com/example/Hello.java", """
                package com.example;

                import dev.isira.jaspring.browser.Platform;

                public class Hello {
                    public void greet(String name) {
                        Platform.onBrowser(client -> "Hello " + name + client.location().hash());
                    }
                }
                """);
    }

    @Test
    void projectsWithoutClosuresSkipTheImageTasks() throws IOException {
        write("src/main/java/com/example/Hello.java", "package com.example;\npublic class Hello {}\n");
        final var result = runner("build").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":scanBrowserClosures").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, result.task(":buildBrowserImage").getOutcome());
        assertFalse(Files.exists(project.resolve("build/resources/main/jaspring/jaspring-browser.json")));
    }

    @Test
    void skipPropertyWarnsAndLeavesNoManifest() throws IOException {
        final var result = runner("build", "-Pjaspring.browser.skip=true").build();
        assertEquals(TaskOutcome.SKIPPED, result.task(":buildBrowserImage").getOutcome());
        assertTrue(result.getOutput().contains("jaspring.browser.skip is set"), result.getOutput());
        assertFalse(Files.exists(project.resolve("build/resources/main/jaspring/jaspring-browser.json")));
        assertTrue(Files.readString(project.resolve("build/jaspring/browser/closures.json")).contains("com.example.Hello"));
    }

    @Test
    void missingAssemblerFailsWithAnActionableMessage() {
        final var result = runner("buildBrowserImage", "-Pjaspring.browser.assemblerPath=/nonexistent/wasm-as").buildAndFail();
        final var output = result.getOutput();
        assertTrue(output.contains("Binaryen") || output.contains("GraalVM"), output);
        assertTrue(output.contains("jaspring.browser.skip"), output);
    }

    @Test
    void buildsLauncherModuleAndManifest() throws IOException {
        assumeTrue(Boolean.getBoolean("jaspring.wasm"), "set -Pjaspring.wasm=true to run the Web Image build");
        assumeTrue(Fixtures.graalvm(), "needs Oracle GraalVM");
        assumeTrue(GraalvmLocator.assembler(null).isPresent(), "needs Binaryen wasm-as >= 119 on the PATH");

        final var started = System.nanoTime();
        final var result = runner("build").build();
        final var seconds = (System.nanoTime() - started) / 1_000_000_000;
        assertEquals(TaskOutcome.SUCCESS, result.task(":buildBrowserImage").getOutcome());
        final var js = project.resolve("build/resources/main/jaspring/wasm/jaspring-app.js");
        final var wasm = project.resolve("build/resources/main/jaspring/wasm/jaspring-app.js.wasm");
        final var manifest = project.resolve("build/resources/main/jaspring/jaspring-browser.json");
        assertTrue(Files.size(js) > 0 && Files.size(wasm) > 0);
        final var json = Files.readString(manifest);
        assertTrue(json.contains("com.example.Hello"), json);
        assertTrue(json.contains("\"wasm\": {\"name\": \"jaspring-app.js.wasm\""), json);
        System.out.printf("Web Image build: %ds, launcher %d bytes, module %d bytes%n", seconds, Files.size(js), Files.size(wasm));

        assertEquals(TaskOutcome.UP_TO_DATE, runner("build").build().task(":buildBrowserImage").getOutcome());
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
