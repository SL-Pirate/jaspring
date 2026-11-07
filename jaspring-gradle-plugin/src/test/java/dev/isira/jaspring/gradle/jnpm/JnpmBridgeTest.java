package dev.isira.jaspring.gradle.jnpm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JnpmBridgeTest {
    @Test void browserAndEditorAdaptersPassTheirBehaviorTests() throws Exception {
        final var workspace = Path.of(System.getProperty("jnpm.test.workspace"));
        final var node = JnpmFiles.read(workspace.resolve("jnpm/toolchain.json")).get("node").getAsString();
        final var output = Files.createTempFile("jnpm-test-", ".log");
        try {
            final var builder = new ProcessBuilder(node, "--test", System.getProperty("jnpm.test.script")).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().put("JNPM_TEST_WORKSPACE", workspace.toString());
            final var process = builder.start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new AssertionError("JNPM adapter tests timed out\n" + Files.readString(output));
            }
            final var report = Files.readString(output);
            System.out.println(report);
            assertEquals(0, process.exitValue(), report);
        } finally { Files.deleteIfExists(output); }
    }
}
