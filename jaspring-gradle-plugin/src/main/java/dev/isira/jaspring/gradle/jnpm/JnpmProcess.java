package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

/// Drains output concurrently and kills the process tree on timeout or Gradle cancellation.
final class JnpmProcess {
    private JnpmProcess() {}

    static void run(List<String> command, Path directory, Map<String, String> environment, Logger logger) throws IOException {
        final var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        // npm_config_* from the caller must not silently change the locked installation policy.
        builder.environment().keySet().removeIf(key -> key.toLowerCase(java.util.Locale.ROOT).startsWith("npm_config_"));
        builder.environment().putAll(environment);
        final var process = builder.start();
        final var output = Thread.startVirtualThread(() -> {
            try (var reader = process.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    for (final var entry : environment.entrySet()) {
                        if (entry.getKey().startsWith("JNPM_TOKEN_") && !entry.getValue().isEmpty()) line = line.replace(entry.getValue(), "[redacted]");
                    }
                    logger.lifecycle("{}", line);
                }
            } catch (IOException ignored) {
                // Process termination closes the pipe; its exit status is checked below.
            }
        });
        try {
            if (!process.waitFor(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS)) {
                throw new GradleException("JNPM tool exceeded its ten-minute deadline");
            }
            output.join();
            if (process.exitValue() != 0) throw new GradleException("JNPM tool failed (exit " + process.exitValue() + "); see the diagnostics above");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("JNPM operation cancelled", e);
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
