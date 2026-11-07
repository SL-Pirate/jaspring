package dev.isira.jaspring.boot.typescript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.io.support.ResourcePatternResolver;

/// Finds the application's `.ts` sources for the startup fallback: the configured directory relative to the
/// working directory when it exists (a checkout run from the IDE), else `classpath:/typescript/` (sources kept in
/// resources, for builds without the Gradle plugin). A packaged application has neither, and the build's manifest
/// stands.
public final class TypescriptSources {

    public static final String CLASSPATH_ROOT = "typescript/";

    /// `path` is relative with `/` separators; the digest is over the file's bytes, like the manifest's.
    public record Source(String path, byte[] bytes) {
        public String sha256() {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        public String text() {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private TypescriptSources() {
    }

    public static List<Source> find(String sourceDir, ResourcePatternResolver resolver) {
        final var directory = Path.of(sourceDir);
        if (Files.isDirectory(directory)) return fromDirectory(directory);
        return fromClasspath(resolver);
    }

    static List<Source> fromDirectory(Path directory) {
        final var sources = new ArrayList<Source>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (final var path : paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".ts")).sorted().toList()) {
                final var relative = directory.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                sources.add(new Source(relative, Files.readAllBytes(path)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read TypeScript sources under " + directory, e);
        }
        return sources;
    }

    private static List<Source> fromClasspath(ResourcePatternResolver resolver) {
        try {
            final var resources = resolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + CLASSPATH_ROOT + "**/*.ts");
            final var sources = new ArrayList<Source>();
            for (final var resource : resources) {
                final var url = resource.getURL().toString();
                final var start = url.lastIndexOf("/" + CLASSPATH_ROOT);
                if (start < 0) continue;
                try (var in = resource.getInputStream()) {
                    sources.add(new Source(url.substring(start + CLASSPATH_ROOT.length() + 1), in.readAllBytes()));
                }
            }
            sources.sort((a, b) -> a.path().compareTo(b.path()));
            return sources;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read TypeScript sources from the classpath", e);
        }
    }}
