package dev.isira.jaspring.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerateComponentTest {
    @TempDir
    Path project;

    @BeforeEach
    void setup() throws IOException {
        write("settings.gradle", "rootProject.name = 'consumer'\n");
        final var coreJar = System.getProperty("jaspring.coreJar").replace("\\", "\\\\").replace("'", "\\'");
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'dev.isira.jaspring'
                }
                dependencies { implementation files('%s') }
                """.formatted(coreJar));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-card", "UserCard", "UserCardComponent"})
    void generatesMatchingPackageResourcesAndCompilableJava(String name) throws IOException {
        generate("--name=" + name).build();
        final var source = read("src/main/java/com/example/usercard/UserCardComponent.java");
        assertTrue(source.contains("package com.example.usercard;"));
        assertTrue(source.contains("selector = \"app-user-card\""));
        assertTrue(source.contains("templateUrl = \"user-card.component.html\""));
        assertTrue(source.contains("styleUrl = \"user-card.component.css\""));
        assertEquals("<p>user-card works!</p>\n", read("src/main/resources/com/example/usercard/user-card.component.html"));
        assertEquals("", read("src/main/resources/com/example/usercard/user-card.component.css"));
        runner("classes").build();
        assertTrue(Files.exists(project.resolve("build/classes/java/main/com/example/usercard/UserCardComponent.class")));
        assertTrue(Files.exists(project.resolve("build/resources/main/com/example/usercard/user-card.component.html")));
    }

    @Test
    void supportsFlatNestedPathsAndSelectorOverride() throws IOException {
        generate("--name=admin/user-card", "--flat", "--prefix=shop", "--selector=profile-card").build();
        assertTrue(read("src/main/java/com/example/admin/UserCardComponent.java").contains("selector = \"profile-card\""));
        assertTrue(Files.exists(project.resolve("src/main/resources/com/example/admin/user-card.component.html")));
    }

    @Test
    void inlineTemplateStylesAndOnPushCompileWithoutResourceFiles() throws IOException {
        generate("--name=counter", "--inline-template", "--inline-style", "--display-block",
                "--change-detection=OnPush", "--prefix=demo").build();
        final var source = read("src/main/java/com/example/counter/CounterComponent.java");
        assertTrue(source.contains("selector = \"demo-counter\""));
        assertTrue(source.contains("template = \"<p>counter works!</p>\""));
        assertTrue(source.contains("styles = \":host {\\n    display: block;\\n}\\n\""));
        assertTrue(source.contains("changeDetection = ChangeDetectionStrategy.ON_PUSH"));
        assertFalse(Files.exists(project.resolve("src/main/resources")));
        runner("classes").build();
    }

    @Test
    void styleNoneOmitsStylesEvenWhenInlineStyleIsRequested() throws IOException {
        generate("--name=card", "--style=none", "--inline-style").build();
        assertFalse(read("src/main/java/com/example/card/CardComponent.java").contains("style"));
        assertFalse(Files.exists(project.resolve("src/main/resources/com/example/card/card.component.css")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/com/example/card/CardComponent.java",
            "src/main/resources/com/example/card/card.component.html",
            "src/main/resources/com/example/card/card.component.css"
    })
    void anyExistingFilePreventsAllWrites(String existing) throws IOException {
        write(existing, "user changes");
        assertTrue(generate("--name=card").buildAndFail().getOutput().contains("Refusing to overwrite"));
        assertEquals("user changes", read(existing));
        try (final var files = Files.walk(project.resolve("src"))) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"--name=../escape", "--name=/absolute", "--name=card/", "--package=com.class",
            "--package=../escape", "--selector=bad\"name", "--style=scss", "--change-detection=Unknown"})
    void invalidArgumentsNeverCreateSources(String invalid) {
        final var arguments = new ArrayList<>(List.of("generateComponent", "--package=com.example", "--name=card"));
        final var key = invalid.substring(0, invalid.indexOf('='));
        arguments.removeIf(argument -> argument.startsWith(key + "="));
        arguments.add(invalid);
        runner(arguments.toArray(String[]::new)).buildAndFail();
        assertFalse(Files.exists(project.resolve("src")));
    }

    @Test
    void missingArgumentsExplainRequiredOptions() {
        assertTrue(runner("generateComponent").buildAndFail().getOutput().contains("Provide --name"));
        assertTrue(runner("generateComponent", "--name=card").buildAndFail().getOutput().contains("Provide --package"));
    }

    @Test
    void failedWriteRemovesFilesCreatedByThisInvocation() throws IOException {
        write("src/main/resources", "not a directory");
        assertTrue(generate("--name=card").buildAndFail().getOutput().contains("Could not generate component"));
        assertFalse(Files.exists(project.resolve("src/main/java/com/example/card/CardComponent.java")));
        assertEquals("not a directory", read("src/main/resources"));
    }

    @Test
    void configurationCacheRetainsOverwriteProtection() {
        generate("--name=card", "--configuration-cache").build();
        final var result = generate("--name=card", "--configuration-cache").buildAndFail();
        assertTrue(result.getOutput().contains("Reusing configuration cache"));
        assertTrue(result.getOutput().contains("Refusing to overwrite"));
    }

    @Test
    void targetsOnlySelectedSubprojectAndSupportsCustomRoots() throws IOException {
        write("settings.gradle", "rootProject.name = 'consumer'\ninclude 'app'\n");
        write("app/build.gradle", """
                plugins { id 'dev.isira.jaspring' }
                tasks.named('generateComponent') {
                    javaDirectory = layout.projectDirectory.dir('sources')
                    resourcesDirectory = layout.projectDirectory.dir('resources')
                }
                """);
        runner(":app:generateComponent", "--name=card", "--package=com.example", "--flat").build();
        assertTrue(Files.exists(project.resolve("app/sources/com/example/CardComponent.java")));
        assertTrue(Files.exists(project.resolve("app/resources/com/example/card.component.html")));
        assertFalse(Files.exists(project.resolve("src")));
    }

    private GradleRunner generate(String... options) {
        final var arguments = new ArrayList<>(List.of("generateComponent", "--package=com.example"));
        arguments.addAll(List.of(options));
        return runner(arguments.toArray(String[]::new));
    }

    private GradleRunner runner(String... arguments) {
        final var all = new ArrayList<>(List.of(arguments));
        all.add("--stacktrace");
        all.add("--offline");
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(all);
    }

    private void write(String path, String content) throws IOException {
        final var target = project.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private String read(String path) throws IOException {
        return Files.readString(project.resolve(path));
    }
}
