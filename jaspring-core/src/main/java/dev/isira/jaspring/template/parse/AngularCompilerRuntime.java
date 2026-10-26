package dev.isira.jaspring.template.parse;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/// Hosts the bundled Angular compiler and PostCSS in one GraalJS context.
///
/// The context is single-threaded, so every call runs under a lock and callers must convert
/// polyglot values to Java inside the callback; values must not escape it. This runtime is
/// used at startup only; nothing on the request path touches it.
public final class AngularCompilerRuntime implements AutoCloseable {

    private static final String BUNDLE_PATH = "/jaspring/js/jaspring-compiler.mjs";

    private final Engine engine;
    private final Context context;
    private final Value exports;
    private final ReentrantLock lock = new ReentrantLock();

    private AngularCompilerRuntime(Engine engine, Context context, Value exports) {
        this.engine = engine;
        this.context = context;
        this.exports = exports;
    }

    public static AngularCompilerRuntime load() {
        final var engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        final var context = Context.newBuilder("js")
                .engine(engine)
                .option("js.esm-eval-returns-exports", "true")
                .option("js.text-encoding", "true")
                .allowHostAccess(HostAccess.NONE)
                .allowIO(IOAccess.NONE)
                .build();
        try {
            final var exports = context.eval(bundleSource());
            return new AngularCompilerRuntime(engine, context, exports);
        } catch (RuntimeException e) {
            context.close();
            engine.close();
            throw e;
        }
    }

    private static Source bundleSource() {
        final var stream = AngularCompilerRuntime.class.getResourceAsStream(BUNDLE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Missing bundle " + BUNDLE_PATH + "; run tools/js-bundle");
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return Source.newBuilder("js", reader, "jaspring-compiler.mjs")
                    .mimeType("application/javascript+module")
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Versions versions() {
        return withExports(exports -> {
            final var value = exports.getMember("versions");
            return new Versions(value.getMember("angular").asString(), value.getMember("postcss").asString());
        });
    }

    /// Runs Angular's `parseTemplate` and hands the raw result to `convert` while the context is locked.
    /// The raw value carries `nodes`, `ngContentSelectors` and `errors` (never thrown by the JS side).
    public <T> T parse(String template, String url, ParseOptions options, Function<Value, T> convert) {
        return withExports(exports -> {
            final var jsOptions = context.eval("js", "({})");
            jsOptions.putMember("preserveWhitespaces", options.preserveWhitespaces());
            jsOptions.putMember("enableBlockSyntax", true);
            jsOptions.putMember("enableLetSyntax", true);
            jsOptions.putMember("enableSelectorless", false);
            jsOptions.putMember("collectCommentNodes", false);
            // Angular returns no nodes at all after a parse error unless asked to keep converting.
            jsOptions.putMember("alwaysAttemptHtmlToR3AstConversion", options.tolerateErrors());
            return convert.apply(exports.getMember("parse").execute(template, url, jsOptions));
        });
    }

    public <T> T domSchema(Function<Value, T> convert) {
        return withExports(exports -> convert.apply(exports.getMember("domSchema").execute()));
    }

    public String processCss(String css, String scopeId) {
        return withExports(exports -> exports.getMember("processCss").execute(css, scopeId).asString());
    }

    private <T> T withExports(Function<Value, T> body) {
        lock.lock();
        try {
            return body.apply(exports);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            context.close();
            engine.close();
        } finally {
            lock.unlock();
        }
    }

    public record Versions(String angular, String postcss) {}
}
