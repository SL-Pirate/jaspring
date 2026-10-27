package dev.isira.jaspring.gradle.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/// Finds the Oracle GraalVM with Web Image and the Binaryen assembler the image build needs, and explains what is
/// missing in terms of what to install or set.
public final class GraalvmLocator {

    public static final int MIN_BINARYEN = 119;
    public static final String SKIP_HINT = "or run with -Pjaspring.browser.skip=true to build without browser closures";

    public record Toolchain(Path graalvmHome, Path nativeImage, Path wasmAs) {}

    private GraalvmLocator() {
    }

    /// `configuredHome`/`configuredAssembler` come from the `jaspring.browser` extension and may be null.
    public static Toolchain locate(Path configuredHome, Path configuredAssembler) {
        final var home = graalvmHome(configuredHome).orElseThrow(() -> new IllegalStateException(
                "Oracle GraalVM was not found: set jaspring.browser.graalvmHome, the GRAALVM_HOME environment variable, or run Gradle on Oracle GraalVM 25.2, "
                        + SKIP_HINT));
        final var nativeImage = home.resolve("bin").resolve(executable("native-image"));
        if (!Files.isExecutable(nativeImage)) {
            throw new IllegalStateException(home + " has no bin/native-image; install Oracle GraalVM 25.2 or later, " + SKIP_HINT);
        }
        if (!Files.isRegularFile(home.resolve("lib/svm/tools/svm-wasm/native-image.properties"))) {
            throw new IllegalStateException(home + " has no Web Image backend (lib/svm/tools/svm-wasm); it is part of Oracle GraalVM 25.1+,"
                    + " GraalVM Community does not include it, " + SKIP_HINT);
        }
        final var wasmAs = assembler(configuredAssembler).orElseThrow(() -> new IllegalStateException(
                "Binaryen's wasm-as (version " + MIN_BINARYEN + " or later) was not found on the PATH: install Binaryen"
                        + " (Fedora: dnf install binaryen; macOS: brew install binaryen; Ubuntu 24.04 packages an older version, use a release from"
                        + " https://github.com/WebAssembly/binaryen/releases) or set jaspring.browser.assemblerPath, " + SKIP_HINT));
        return new Toolchain(home, nativeImage, wasmAs);
    }

    public static Optional<Path> graalvmHome(Path configured) {
        if (configured != null) return Optional.of(configured);
        final var env = System.getenv("GRAALVM_HOME");
        if (env != null && !env.isBlank()) return Optional.of(Path.of(env));
        final var current = Path.of(System.getProperty("java.home"));
        if (System.getProperty("java.vendor.version", "").contains("GraalVM")) return Optional.of(current);
        return Optional.empty();
    }

    static Optional<Path> assembler(Path configured) {
        if (configured != null) return Files.isExecutable(configured) ? Optional.of(configured) : Optional.empty();
        final var path = System.getenv("PATH");
        if (path == null) return Optional.empty();
        for (final var entry : path.split(java.io.File.pathSeparator)) {
            final var candidate = Path.of(entry, executable("wasm-as"));
            if (Files.isExecutable(candidate) && binaryenVersion(candidate) >= MIN_BINARYEN) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /// The major version `wasm-as --version` prints (`wasm-as version 126 (version_126)`), or -1.
    static int binaryenVersion(Path wasmAs) {
        try {
            final var process = new ProcessBuilder(wasmAs.toString(), "--version").redirectErrorStream(true).start();
            final var output = new String(process.getInputStream().readAllBytes());
            process.waitFor(10, TimeUnit.SECONDS);
            final var matcher = Pattern.compile("version[ _](\\d+)").matcher(output);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    public static String version(Path tool) {
        try {
            final var process = new ProcessBuilder(tool.toString(), "--version").redirectErrorStream(true).start();
            final var output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(30, TimeUnit.SECONDS);
            return output.lines().findFirst().orElse("");
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? name + ".exe" : name;
    }
}
