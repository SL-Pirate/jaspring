package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.gradle.api.GradleException;

/// Installs a checksum-pinned Node distribution. The npm CLI is run through Node, so archive bin symlinks are
/// unnecessary; no dependency install scripts or external archive tools are involved.
final class JnpmToolchain {
    private JnpmToolchain() {}

    static Path node(Path home) { return home.resolve(windows() ? "node.exe" : "bin/node"); }
    static Path npm(Path home) { return home.resolve(windows() ? "node_modules/npm/bin/npm-cli.js" : "lib/node_modules/npm/bin/npm-cli.js"); }
    static boolean windows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }

    static Path install(Path cache, boolean offline) throws IOException {
        final var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        final var platform = os.contains("win") ? "win" : os.contains("mac") ? "darwin" : os.contains("linux") ? "linux" : os;
        final var arch = switch (System.getProperty("os.arch")) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> System.getProperty("os.arch");
        };
        final var classifier = platform + "-" + arch;
        final var checksum = JnpmFiles.VERSIONS.getProperty(classifier);
        if (checksum == null) throw new GradleException("JNPM has no managed Node distribution for " + classifier + "; configure jnpm.nodeHome");
        final var name = "node-v" + JnpmFiles.VERSIONS.getProperty("node") + "-" + classifier;
        final var destination = cache.resolve(name);
        Files.createDirectories(cache);
        try (var channel = FileChannel.open(cache.resolve(name + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            if (Files.isExecutable(node(destination)) && Files.isRegularFile(npm(destination))) return destination;
            if (offline) throw new GradleException("Managed Node is not cached; run a JNPM task once online or configure jnpm.nodeHome");
            final var archive = Files.createTempFile(cache, "node-", ".download");
            final var staging = Files.createTempDirectory(cache, "node-");
            try {
                final var extension = platform.equals("win") ? ".zip" : ".tar.gz";
                final var uri = URI.create("https://nodejs.org/dist/v" + JnpmFiles.VERSIONS.getProperty("node") + "/" + name + extension);
                try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build()) {
                    final var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).build(), HttpResponse.BodyHandlers.ofFile(archive));
                    if (response.statusCode() != 200) throw new IOException("Node download returned HTTP " + response.statusCode());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GradleException("Node download cancelled", e);
                }
                if (!checksum.equals(JnpmFiles.sha256(archive))) throw new IOException("Node archive checksum does not match the pinned toolchain");
                if (platform.equals("win")) unzip(archive, staging); else untar(archive, staging);
                node(staging.resolve(name)).toFile().setExecutable(true, false);
                if (Files.exists(destination)) delete(destination);
                Files.move(staging.resolve(name), destination, StandardCopyOption.REPLACE_EXISTING);
                return destination;
            } finally {
                Files.deleteIfExists(archive);
                delete(staging);
            }
        }
    }

    private static Path target(Path root, String name) throws IOException {
        final var result = root.resolve(name).normalize();
        if (!result.startsWith(root) || name.contains("\\")) throw new IOException("Invalid archive path: " + name);
        return result;
    }

    private static void unzip(Path archive, Path root) throws IOException {
        try (var input = new ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                final var path = target(root, entry.getName());
                if (entry.isDirectory()) Files.createDirectories(path);
                else {
                    Files.createDirectories(path.getParent());
                    Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void untar(Path archive, Path root) throws IOException {
        try (var input = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                final var path = target(root, entry.getName());
                if (entry.isDirectory()) Files.createDirectories(path);
                else if (entry.isFile()) {
                    Files.createDirectories(path.getParent());
                    Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
                    if ((entry.getMode() & 0111) != 0) path.toFile().setExecutable(true, false);
                }
            }
        }
    }

    static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (final var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
