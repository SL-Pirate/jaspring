package dev.isira.jaspring.typescript.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import dev.isira.jaspring.typescript.TypescriptException;

/// The bundle in a Node process: the driver reads one request on stdin and writes one result on stdout. A fresh
/// process per call keeps the host stateless; Node loads the bundle in a few hundred milliseconds.
public final class NodeBundleHost implements BundleHost {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final Path node;
    private final Path driver;

    private NodeBundleHost(Path node, Path driver) {
        this.node = node;
        this.driver = driver;
    }

    /// `node` null means the first Node 20+ on the PATH.
    public static NodeBundleHost load(Path node) {
        final var executable = node != null ? node : NodeLocator.find().orElseThrow(() -> new TypescriptException(
                "Node " + NodeLocator.MIN_MAJOR + " or later was not found on the PATH (" + System.getenv("PATH")
                        + "); install it, name the executable, or use Host.GRAALJS"));
        if (!Files.isExecutable(executable)) throw new TypescriptException("Not an executable: " + executable);
        return new NodeBundleHost(executable, Bundle.extractForNode());
    }

    @Override
    public String run(String requestJson) {
        try {
            final var process = new ProcessBuilder(node.toString(), driver.toString()).start();
            final var stderr = new ByteArrayOutputStream();
            final var drain = Thread.startVirtualThread(() -> {
                try (var in = process.getErrorStream()) {
                    in.transferTo(stderr);
                } catch (IOException ignored) {
                    // the exit code reports the failure
                }
            });
            // The driver reads all of stdin before it writes, so writing then reading cannot deadlock.
            try (var in = process.getOutputStream()) {
                in.write(requestJson.getBytes(StandardCharsets.UTF_8));
            }
            final var stdout = process.getInputStream().readAllBytes();
            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new TypescriptException("Node did not finish compiling within " + TIMEOUT);
            }
            drain.join();
            if (process.exitValue() != 0) {
                throw new TypescriptException("Node exited with " + process.exitValue() + ": " + stderr.toString(StandardCharsets.UTF_8).trim());
            }
            return new String(stdout, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TypescriptException("Cannot run " + node, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypescriptException("Interrupted while waiting for Node", e);
        }
    }

    @Override
    public boolean interpreted() {
        return false;
    }

    @Override
    public void close() {
    }
}
