package dev.isira.jaspring.gradle.jnpm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

final class JnpmFiles {
    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    static final Properties VERSIONS = versions();

    private JnpmFiles() {}

    static Properties versions() {
        final var properties = new Properties();
        try (var input = JnpmFiles.class.getResourceAsStream("/jaspring/jnpm/toolchain.properties")) {
            if (input == null) throw new IOException("Missing JNPM toolchain metadata");
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonObject read(Path path) throws IOException {
        return JSON.fromJson(Files.readString(path), JsonObject.class);
    }

    static void write(Path path, Object value) throws IOException {
        writeText(path, JSON.toJson(value) + "\n");
    }

    static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path) && Files.readString(path).equals(text)) return;
        final var temporary = Files.createTempFile(path.getParent(), ".jnpm-", ".tmp");
        try {
            Files.writeString(temporary, text);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String sha256(Path path) throws IOException { return sha256(Files.readAllBytes(path)); }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Path bridge(Path directory) throws IOException {
        final var target = directory.resolve("bridge.mjs");
        try (var input = JnpmFiles.class.getResourceAsStream("/jaspring/jnpm/bridge.mjs")) {
            if (input == null) throw new IOException("Missing JNPM bridge");
            writeText(target, new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return target;
    }

    static Map<String, String> toolDependencies(boolean lint) {
        final var dependencies = new java.util.TreeMap<String, String>();
        for (final var name : lint ? java.util.List.of("esbuild", "typescript", "eslint", "@eslint/js", "typescript-eslint", "globals")
                : java.util.List.of("esbuild", "typescript")) dependencies.put(name, VERSIONS.getProperty(name));
        return dependencies;
    }
}
