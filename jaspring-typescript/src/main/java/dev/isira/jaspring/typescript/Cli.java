package dev.isira.jaspring.typescript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import dev.isira.jaspring.typescript.internal.Bundle;
import dev.isira.jaspring.typescript.internal.Json;

/// Compiles a source tree from the command line. The Gradle plugin runs this in a worker (reflectively, so the
/// plugin needs no dependency on this module); it is also usable by hand.
///
/// ```
/// --source-dir <dir> --out <dir> --manifest <file> --diagnostics <file> [--no-type-check] [--source-map]
///     [--host graaljs|node] [--node <executable>]
/// ```
///
/// Every `**/*.ts` under the source directory is compiled. Outputs are written under `--out` with their relative
/// paths, the manifest (`{"version":1,"typescript":"6.0.3","files":[{"source","sha256","output"}]}`, digests of
/// the source bytes, `output` null for declaration files) to `--manifest`, and tsc-style diagnostic lines to
/// `--diagnostics`. Exit code 0 means success, 1 type or syntax errors, 2 a crash. Callers own the output
/// directory: nothing is deleted here.
public final class Cli {

    public static final int MANIFEST_VERSION = 1;

    private static final String USAGE = "usage: --source-dir <dir> --out <dir> --manifest <file> --diagnostics <file>"
            + " [--no-type-check] [--source-map] [--host graaljs|node] [--node <executable>]";

    private static TypescriptCompiler warm;
    private static String warmKey;

    private Cli() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        final Arguments arguments;
        try {
            arguments = Arguments.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("jaspring-typescript: " + e.getMessage());
            System.err.println(USAGE);
            return 2;
        }
        try {
            return execute(arguments);
        } catch (RuntimeException | IOException e) {
            System.err.println("jaspring-typescript: " + e.getMessage());
            return 2;
        }
    }

    private static int execute(Arguments arguments) throws IOException {
        final var sources = readSources(arguments.sourceDir());
        final var compiler = compiler(arguments.host(), arguments.node());
        if (compiler.interpreted()) {
            System.err.println("jaspring-typescript: GraalJS runs in the interpreter, so type checking is slow;"
                    + " run on GraalVM with --enable-native-access=ALL-UNNAMED, use --host node, or --no-type-check");
        }
        final var result = compiler.compile(new CompileRequest(sources.stream().map(Source::file).toList(),
                new CompileOptions(arguments.typeCheck(), arguments.sourceMap())));

        for (final var output : result.outputs()) {
            final var target = arguments.out().resolve(output.path());
            Files.createDirectories(target.getParent());
            Files.writeString(target, output.text(), StandardCharsets.UTF_8);
        }
        Files.createDirectories(arguments.manifest().toAbsolutePath().getParent());
        Files.writeString(arguments.manifest(), manifest(compiler.version(), sources), StandardCharsets.UTF_8);
        Files.createDirectories(arguments.diagnostics().toAbsolutePath().getParent());
        final var lines = new StringBuilder();
        for (final var diagnostic : result.diagnostics()) lines.append(diagnostic.format()).append('\n');
        Files.writeString(arguments.diagnostics(), lines.toString(), StandardCharsets.UTF_8);
        return result.hasErrors() ? 1 : 0;
    }

    /// The GraalJS host costs seconds to load; a worker daemon that runs this class again reuses it.
    private static synchronized TypescriptCompiler compiler(Host host, Path node) {
        final var key = host + ":" + node;
        if (warm == null || !key.equals(warmKey)) {
            if (warm != null) warm.close();
            warm = TypescriptCompiler.load(host, node);
            warmKey = key;
        }
        return warm;
    }

    private record Source(SourceFile file, String sha256) {}

    private static List<Source> readSources(Path sourceDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) throw new IllegalArgumentException("Not a directory: " + sourceDir);
        final var sources = new ArrayList<Source>();
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            for (final var path : paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".ts")).sorted().toList()) {
                final var bytes = Files.readAllBytes(path);
                final var relative = sourceDir.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                sources.add(new Source(new SourceFile(relative, new String(bytes, StandardCharsets.UTF_8)), Bundle.sha256(bytes)));
            }
        }
        return sources;
    }

    static String manifest(String typescriptVersion, List<Source> sources) {
        final var files = new ArrayList<Map<String, Object>>();
        for (final var source : sources) {
            final var entry = new LinkedHashMap<String, Object>();
            entry.put("source", source.file().path());
            entry.put("sha256", source.sha256());
            entry.put("output", source.file().outputPath());
            files.add(entry);
        }
        final var manifest = new LinkedHashMap<String, Object>();
        manifest.put("version", MANIFEST_VERSION);
        manifest.put("typescript", typescriptVersion);
        manifest.put("files", files);
        return Json.write(manifest) + "\n";
    }

    record Arguments(Path sourceDir, Path out, Path manifest, Path diagnostics, boolean typeCheck, boolean sourceMap, Host host, Path node) {

        static Arguments parse(String[] args) {
            Path sourceDir = null, out = null, manifest = null, diagnostics = null, node = null;
            var typeCheck = true;
            var sourceMap = false;
            var host = Host.GRAALJS;
            for (var i = 0; i < args.length; i++) {
                final var arg = args[i];
                switch (arg) {
                    case "--no-type-check" -> typeCheck = false;
                    case "--source-map" -> sourceMap = true;
                    case "--source-dir", "--out", "--manifest", "--diagnostics", "--host", "--node" -> {
                        if (i + 1 >= args.length) throw new IllegalArgumentException(arg + " needs a value");
                        final var value = args[++i];
                        switch (arg) {
                            case "--source-dir" -> sourceDir = Path.of(value);
                            case "--out" -> out = Path.of(value);
                            case "--manifest" -> manifest = Path.of(value);
                            case "--diagnostics" -> diagnostics = Path.of(value);
                            case "--node" -> node = Path.of(value);
                            default -> host = switch (value.toLowerCase(Locale.ROOT)) {
                                case "graaljs" -> Host.GRAALJS;
                                case "node" -> Host.NODE;
                                default -> throw new IllegalArgumentException("Unknown host '" + value + "' (graaljs or node)");
                            };
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown argument '" + arg + "'");
                }
            }
            if (sourceDir == null || out == null || manifest == null || diagnostics == null) {
                throw new IllegalArgumentException("--source-dir, --out, --manifest and --diagnostics are required");
            }
            return new Arguments(sourceDir, out, manifest, diagnostics, typeCheck, sourceMap, host, node);
        }
    }}
