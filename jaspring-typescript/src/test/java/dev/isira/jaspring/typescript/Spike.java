package dev.isira.jaspring.typescript;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;

/// Not a test: a timing run of the raw bundle on this JVM. Arguments: a directory holding `.ts` files and a
/// `jaspring.d.ts`.
public final class Spike {

    public static void main(String[] args) throws IOException {
        final var dir = Path.of(args[0]);
        final var interpreter = Boolean.getBoolean("jaspring.spike.interpreter");
        final var engineBuilder = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false");
        if (interpreter) engineBuilder.allowExperimentalOptions(true).option("engine.Compilation", "false");
        final var engine = engineBuilder.build();
        System.out.println("engine: " + engine.getImplementationName() + (interpreter ? " (compilation off)" : "") + ", " + System.getProperty("java.vendor.version"));
        final var before = usedHeap();
        var t = System.nanoTime();
        final var context = Context.newBuilder("js").engine(engine)
                .option("js.esm-eval-returns-exports", "true")
                .option("js.text-encoding", "true")
                .allowHostAccess(HostAccess.NONE)
                .allowIO(IOAccess.NONE)
                .build();
        final var exports = context.eval(bundle());
        final var run = exports.getMember("run");
        System.out.printf("load: %d ms%n", ms(t));
        System.out.println(run.execute("{\"op\":\"version\"}").asString());

        final var files = new ArrayList<String>();
        try (Stream<Path> paths = Files.list(dir)) {
            for (final var path : paths.sorted().toList()) {
                final var name = path.getFileName().toString();
                if (name.endsWith(".ts") && !name.endsWith(".d.ts")) files.add(file(name, Files.readString(path)));
            }
        }
        final var types = file("jaspring.d.ts", Files.readString(dir.resolve("jaspring.d.ts")));
        t = System.nanoTime();
        var result = run.execute("{\"op\":\"transpile\",\"file\":" + files.get(0) + ",\"options\":{}}").asString();
        System.out.printf("transpile: %d ms, %d chars%n", ms(t), result.length());
        final var request = "{\"op\":\"compile\",\"files\":[" + String.join(",", files) + "],\"types\":[" + types + "],\"options\":{\"typeCheck\":true}}";
        for (final var round : List.of("cold", "warm", "warm2")) {
            t = System.nanoTime();
            result = run.execute(request).asString();
            System.out.printf("compile(typeCheck) %s: %d ms, %d chars, diagnostics: %s%n", round, ms(t), result.length(),
                    result.substring(result.indexOf("\"diagnostics\"")).replaceAll("\\s+", " "));
        }
        t = System.nanoTime();
        result = run.execute(request.replace("\"typeCheck\":true", "\"typeCheck\":false")).asString();
        System.out.printf("compile(no check): %d ms, %d chars%n", ms(t), result.length());
        System.gc();
        System.out.printf("heap delta: %d MB%n", (usedHeap() - before) / 1048576);
        context.close();
        engine.close();
    }

    private static String file(String path, String text) {
        return "{\"path\":\"" + path + "\",\"text\":" + quote(text) + "}";
    }

    private static String quote(String text) {
        final var sb = new StringBuilder("\"");
        for (final var c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    private static long ms(long since) {
        return (System.nanoTime() - since) / 1_000_000;
    }

    private static long usedHeap() {
        final var runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static Source bundle() throws IOException {
        try (var reader = new InputStreamReader(Spike.class.getResourceAsStream("/jaspring/typescript/jaspring-typescript.mjs"), StandardCharsets.UTF_8)) {
            return Source.newBuilder("js", reader, "jaspring-typescript.mjs").mimeType("application/javascript+module").build();
        }
    }
}
