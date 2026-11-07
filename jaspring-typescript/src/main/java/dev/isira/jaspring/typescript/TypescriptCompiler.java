package dev.isira.jaspring.typescript;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import dev.isira.jaspring.typescript.internal.Bundle;
import dev.isira.jaspring.typescript.internal.BundleHost;
import dev.isira.jaspring.typescript.internal.GraalBundleHost;
import dev.isira.jaspring.typescript.internal.NodeBundleHost;
import dev.isira.jaspring.typescript.internal.RequestCodec;

/// The TypeScript compiler bundled in this jar (`typescript` 6.x, the last JavaScript release of the compiler),
/// hosted in GraalJS or in a Node process. Every program gets the standard libs (`es2022`, `dom`) and the
/// `jaspring.d.ts` typings; options are fixed, see [CompileOptions].
///
/// Loading the GraalJS host parses a 6 MB script and takes a few seconds; keep one instance and reuse it.
/// Instances are thread-safe. Close them to free the context.
public final class TypescriptCompiler implements AutoCloseable {

    private final Host host;
    private final BundleHost bundle;
    private final List<SourceFile> types;

    private TypescriptCompiler(Host host, BundleHost bundle) {
        this.host = host;
        this.bundle = bundle;
        this.types = List.of(new SourceFile(Bundle.TYPES, jaspringTypes()));
    }

    public static TypescriptCompiler load() {
        return load(Host.GRAALJS, null);
    }

    public static TypescriptCompiler load(Host host) {
        return load(host, null);
    }

    /// `nodeExecutable` applies to [Host#NODE] only; null means the first Node 20+ on the PATH. Throws
    /// [TypescriptException] when the host cannot start.
    public static TypescriptCompiler load(Host host, Path nodeExecutable) {
        final BundleHost bundle = switch (host) {
            case GRAALJS -> GraalBundleHost.load();
            case NODE -> NodeBundleHost.load(nodeExecutable);
        };
        return new TypescriptCompiler(host, bundle);
    }

    public Host host() {
        return host;
    }

    /// True when GraalJS runs without its optimizing compiler, which makes a type-checked compile several times
    /// slower. Running on GraalVM with `--enable-native-access=ALL-UNNAMED` fixes it.
    public boolean interpreted() {
        return bundle.interpreted();
    }

    /// The bundled TypeScript version.
    public String version() {
        return (String) RequestCodec.object(bundle.run(RequestCodec.version())).get("typescript");
    }

    /// The fixed compiler options as the bundle holds them, in tsconfig form.
    public Map<String, Object> options() {
        return RequestCodec.object(bundle.run(RequestCodec.options()));
    }

    /// Compiles one program. Type errors come back as diagnostics, never as exceptions.
    public CompileResult compile(CompileRequest request) {
        return RequestCodec.result(bundle.run(RequestCodec.compile(request, types, false)));
    }

    /// Checks a package-aware program without emitting JavaScript; a bundler owns its runtime output.
    public CompileResult check(CompileRequest request) {
        return RequestCodec.result(bundle.run(RequestCodec.compile(request, types, true)));
    }

    /// Transpiles one file alone, without type checking: the same JavaScript [#compile] would emit for it.
    public CompileResult transpile(SourceFile file) {
        return RequestCodec.result(bundle.run(RequestCodec.transpile(file, CompileOptions.transpileOnly())));
    }

    /// The typings of the `jaspring` browser global, for editors; the compiler adds them to every program itself.
    public static String jaspringTypes() {
        return Bundle.text(Bundle.TYPES);
    }

    /// A `tsconfig.json` with the fixed options and `noEmit`, for editors to extend.
    public static String baseTsconfig() {
        return Bundle.text(Bundle.TSCONFIG);
    }

    @Override
    public void close() {
        bundle.close();
    }
}
