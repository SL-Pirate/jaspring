package dev.isira.jaspring.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import org.gradle.api.GradleException;

/// Copies `jaspring.d.ts` and `tsconfig.base.json` out of the compiler jar, so an editor can type-check
/// `src/main/typescript` with the options and typings the build uses.
final class TypescriptTypes {

    static final String RESOURCE_DIRECTORY = "jaspring/typescript/";
    static final List<String> FILES = List.of("jaspring.d.ts", "tsconfig.base.json");

    private TypescriptTypes() {
    }

    static void extract(Iterable<File> compilerClasspath, Path target) throws IOException {
        Files.createDirectories(target);
        for (final var name : FILES) {
            final var bytes = find(compilerClasspath, name);
            if (bytes == null) {
                throw new GradleException("No " + RESOURCE_DIRECTORY + name + " on the " + JaspringPlugin.TYPESCRIPT_CONFIGURATION
                        + " configuration; it needs the dev.isira:jaspring-typescript jar");
            }
            Files.write(target.resolve(name), bytes);
        }
    }

    private static byte[] find(Iterable<File> classpath, String name) throws IOException {
        for (final var file : classpath) {
            if (file.isDirectory()) {
                final var candidate = file.toPath().resolve(RESOURCE_DIRECTORY + name);
                if (Files.isRegularFile(candidate)) return Files.readAllBytes(candidate);
                continue;
            }
            if (!file.getName().endsWith(".jar")) continue;
            try (var zip = new ZipFile(file)) {
                final var entry = zip.getEntry(RESOURCE_DIRECTORY + name);
                if (entry == null) continue;
                try (var in = zip.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
        }
        return null;
    }
}
