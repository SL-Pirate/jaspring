package dev.isira.jaspring.boot.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import dev.isira.jaspring.boot.JaspringAutoConfiguration;

class TypescriptAutoConfigurationTest {

    @TempDir
    static Path broken;

    @BeforeAll
    static void writeBrokenSource() throws IOException {
        Files.writeString(broken.resolve("hello.ts"), "const n: number = 'x';\nconsole.log(n);\n");
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TypescriptAutoConfiguration.class, JaspringAutoConfiguration.class))
            .withPropertyValues("jaspring.base-packages=dev.isira.jaspring.boot.testapp", "jaspring.typescript.source-dir=" + broken);

    @Test
    void typeErrorsFailStartupWithTscStyleMessages() {
        runner.run(context -> {
            final var failure = context.getStartupFailure();
            assertNotNull(failure);
            final var messages = new StringBuilder();
            for (Throwable t = failure; t != null; t = t.getCause()) messages.append(t.getMessage()).append('\n');
            assertTrue(messages.toString().contains("hello.ts(1,7): error TS2322"), messages.toString());
        });
    }

    @Test
    void transpileOnlySkipsTheTypeErrors() {
        runner.withPropertyValues("jaspring.typescript.type-check=false").run(context -> {
            assertTrue(context.getBean(TypescriptScripts.class).inMemory("hello.js").isPresent());
        });
    }

    @Test
    void disabledFallbackKeepsTheManifest() {
        runner.withPropertyValues("jaspring.typescript.enabled=false").run(context -> {
            final var scripts = context.getBean(TypescriptScripts.class);
            assertEquals(1, scripts.scripts().size());
            assertTrue(scripts.inMemory("hello.js").isEmpty());
        });
    }
}
