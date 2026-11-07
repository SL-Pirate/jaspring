package dev.isira.jaspring.typescript.internal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.graalvm.polyglot.Source;

/// The resources under `jaspring/typescript/`: the compiler bundle built by `tools/js-bundle`, the Node driver,
/// the public typings and the editor tsconfig.
public final class Bundle {

    public static final String DIRECTORY = "/jaspring/typescript/";
    public static final String BUNDLE = "jaspring-typescript.mjs";
    public static final String NODE_DRIVER = "jaspring-typescript-node.mjs";
    public static final String TYPES = "jaspring.d.ts";
    public static final String TSCONFIG = "tsconfig.base.json";

    private Bundle() {
    }

    public static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    public static byte[] bytes(String name) {
        try (var stream = Bundle.class.getResourceAsStream(DIRECTORY + name)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + DIRECTORY + name + "; run tools/js-bundle");
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Source source() {
        try (var stream = Bundle.class.getResourceAsStream(DIRECTORY + BUNDLE)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + DIRECTORY + BUNDLE + "; run tools/js-bundle");
            return Source.newBuilder("js", new InputStreamReader(stream, StandardCharsets.UTF_8), BUNDLE)
                    .mimeType("application/javascript+module")
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Node cannot import from a jar, so the bundle and the driver are extracted once per bundle digest under the
    /// temp directory. Returns the driver's path. Concurrent extractions race on an atomic rename; the loser
    /// discards its copy.
    public static Path extractForNode() {
        final var bundle = bytes(BUNDLE);
        final var root = Path.of(System.getProperty("java.io.tmpdir"), "jaspring-typescript");
        final var dir = root.resolve(sha256(bundle).substring(0, 16));
        final var driver = dir.resolve(NODE_DRIVER);
        if (Files.isRegularFile(driver) && Files.isRegularFile(dir.resolve(BUNDLE))) return driver;
        try {
            Files.createDirectories(root);
            final var staging = Files.createTempDirectory(root, "extract");
            Files.write(staging.resolve(BUNDLE), bundle);
            Files.write(staging.resolve(NODE_DRIVER), bytes(NODE_DRIVER));
            try {
                Files.move(staging, dir, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException raced) {
                Files.deleteIfExists(staging.resolve(BUNDLE));
                Files.deleteIfExists(staging.resolve(NODE_DRIVER));
                Files.deleteIfExists(staging);
            }
            return driver;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot extract the TypeScript bundle for Node under " + root, e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
