package dev.isira.jaspring.boot.typescript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import dev.isira.jaspring.boot.JaspringProperties;
import dev.isira.jaspring.typescript.CompileOptions;
import dev.isira.jaspring.typescript.CompileRequest;
import dev.isira.jaspring.typescript.Host;
import dev.isira.jaspring.typescript.SourceFile;
import dev.isira.jaspring.typescript.TypescriptCompiler;

/// Decides whether the build's output still matches the sources and, when it does not, compiles them once at
/// startup. Needs `jaspring-typescript` on the classpath; only `TypescriptAutoConfiguration` loads this class.
public final class TypescriptStartupCompiler {

    private static final Logger log = LoggerFactory.getLogger(TypescriptStartupCompiler.class);

    private TypescriptStartupCompiler() {
    }

    /// The manifest is authoritative when no sources are found (a packaged application). Otherwise it is stale when
    /// the set of sources or any digest differs.
    static boolean stale(TypescriptScripts manifest, List<TypescriptSources.Source> sources) {
        if (sources.isEmpty()) return false;
        if (manifest.scripts().size() != sources.size()) return true;
        final var digests = new HashMap<String, String>();
        manifest.scripts().forEach(script -> digests.put(script.source(), script.sha256()));
        for (final var source : sources) {
            if (!source.sha256().equals(digests.get(source.path()))) return true;
        }
        return false;
    }

    public static TypescriptScripts compileIfStale(JaspringProperties.Typescript settings, TypescriptScripts manifest, List<TypescriptSources.Source> sources) {
        if (!sources.isEmpty() && (manifest.bundled() || Files.exists(Path.of("jnpm.lock")))) {
            if (stale(manifest, sources) || !bundleInputsMatch(Path.of(settings.getSourceDir()), Path.of("jnpm.lock"), manifest)) {
                throw new TypescriptCompileException("JNPM browser bundles are missing or stale; run ./gradlew compileTypescript before starting the application");
            }
            return manifest;
        }
        if (!stale(manifest, sources)) return manifest;
        final var started = System.nanoTime();
        final var host = settings.getHost() == JaspringProperties.Typescript.Host.NODE ? Host.NODE : Host.GRAALJS;
        final var files = sources.stream().map(source -> new SourceFile(source.path(), source.text())).toList();
        final String version;
        final dev.isira.jaspring.typescript.CompileResult result;
        try (var compiler = TypescriptCompiler.load(host)) {
            if (compiler.interpreted()) {
                log.warn("GraalJS runs in the interpreter, so compiling TypeScript at startup is slow: run on GraalVM with"
                        + " --enable-native-access=ALL-UNNAMED, set jaspring.typescript.host=node, or jaspring.typescript.type-check=false");
            }
            version = compiler.version();
            result = compiler.compile(new CompileRequest(files, new CompileOptions(settings.isTypeCheck(), false)));
        }
        if (result.hasErrors()) {
            final var lines = new StringBuilder("TypeScript errors in " + settings.getSourceDir() + ":");
            result.errors().forEach(error -> lines.append("\n  ").append(error.format()));
            throw new TypescriptCompileException(lines.toString());
        }
        result.diagnostics().forEach(diagnostic -> log.warn("{}", diagnostic.format()));
        final var scripts = new ArrayList<TypescriptScripts.Script>();
        final var startedAt = System.currentTimeMillis();
        for (final var source : sources) {
            final var output = outputPath(source.path());
            final Resource resource = output == null ? null : result.output(output).map(emitted -> inMemory(output, emitted.text(), startedAt)).orElse(null);
            scripts.add(new TypescriptScripts.Script(source.path(), source.sha256(), output, resource));
        }
        log.info("Compiled {} TypeScript file(s) from {} with TypeScript {} on {}{} in {} ms ({})", files.size(), settings.getSourceDir(), version, host,
                settings.isTypeCheck() ? "" : " without type checking", (System.nanoTime() - started) / 1_000_000,
                manifest.isEmpty() ? "no build manifest" : "the build manifest is stale");
        return new TypescriptScripts(version, scripts);
    }

    static boolean bundleInputsMatch(Path sourceDirectory, Path lock, TypescriptScripts manifest) {
        if (!manifest.bundled()) return false;
        try {
            if (!Files.isRegularFile(lock) || !sha256(lock).equals(manifest.lockSha256())) return false;
            if (!Files.isDirectory(sourceDirectory)) return false;
            final var current = new TreeMap<String, String>();
            try (var paths = Files.walk(sourceDirectory)) {
                for (final var file : paths.filter(Files::isRegularFile).toList()) {
                    current.put(sourceDirectory.relativize(file).toString().replace(java.io.File.separatorChar, '/'), sha256(file));
                }
            }
            return current.equals(manifest.inputs());
        } catch (IOException e) {
            throw new TypescriptCompileException("Cannot verify JNPM build inputs: " + e.getMessage());
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String outputPath(String source) {
        return source.endsWith(".d.ts") ? null : source.substring(0, source.length() - ".ts".length()) + ".js";
    }

    private static Resource inMemory(String name, String text, long lastModified) {
        return new ByteArrayResource(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name.substring(name.lastIndexOf('/') + 1);
            }

            @Override
            public long lastModified() {
                return lastModified;
            }
        };
    }
}
