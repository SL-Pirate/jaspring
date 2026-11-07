package dev.isira.jaspring.typescript.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/// Finds a Node executable on the PATH that is new enough to load the bundle.
public final class NodeLocator {

    public static final int MIN_MAJOR = 20;

    private NodeLocator() {
    }

    public static Optional<Path> find() {
        final var path = System.getenv("PATH");
        if (path == null) return Optional.empty();
        final var name = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "node.exe" : "node";
        for (final var entry : path.split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            final var candidate = Path.of(entry, name);
            if (Files.isExecutable(candidate) && majorVersion(candidate) >= MIN_MAJOR) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /// The major version `node --version` prints (`v22.23.1`), or -1.
    public static int majorVersion(Path node) {
        try {
            final var process = new ProcessBuilder(node.toString(), "--version").redirectErrorStream(true).start();
            final var output = new String(process.getInputStream().readAllBytes());
            process.waitFor(10, TimeUnit.SECONDS);
            final var matcher = Pattern.compile("v?(\\d+)\\.").matcher(output);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
