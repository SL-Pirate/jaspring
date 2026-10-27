package dev.isira.jaspring.gradle.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/// Compiles fixture sources against the core jar, so scanner tests see real javac output (serializable lambda
/// names include a hash: `lambda$save$<hash>$0`).
final class Fixtures {

    static final Path CORE_JAR = Path.of(System.getProperty("jaspring.coreJar"));

    static final String COMPONENT = """
            package fx;

            import java.util.List;
            import java.util.Optional;
            import dev.isira.jaspring.browser.Geolocation.Position;
            import dev.isira.jaspring.browser.Platform;

            @Deprecated
            public class Comp {
                public record Spot(String city, int zoom, List<String> tags, Optional<Kind> kind) {}
                public enum Kind { CITY, TOWN }

                public String field = "f";
                public static int counter;

                public Comp() {
                    Platform.onBrowser(client -> client.location().hash());
                }

                public void ngOnInit() {
                    Platform.onBrowser(client -> 1);
                }

                public void save(String city, int zoom) {
                    Platform.onBrowser(client -> new Spot(city + client.location().hash(), zoom, List.of("a"), Optional.of(Kind.CITY)));
                }

                public void locate() {
                    Platform.onBrowserAsync(Position.class, client -> client.geolocation().current());
                }

                public void kind(Kind kind, int[] values) {
                    Platform.onBrowser(client -> values.length > 0 ? kind : Kind.TOWN);
                }

                public int plain() {
                    return 1;
                }
            }
            """;

    static final String CAPTURES_THIS = """
            package fx;

            import dev.isira.jaspring.browser.Platform;

            public class CapturesThis {
                String field = "f";
                public void go() { Platform.onBrowser(client -> field + client.location().hash()); }
            }
            """;

    static final String TOP_LEVEL_LIST = """
            package fx;

            import java.util.List;
            import dev.isira.jaspring.browser.Platform;

            public class TopLevelList {
                public void go(List<String> names) { Platform.onBrowser(client -> names.size()); }
            }
            """;

    static final String PROMISE_WITHOUT_TOKEN = """
            package fx;

            import dev.isira.jaspring.browser.Platform;

            public class PromiseWithoutToken {
                public void go() { Platform.onBrowser(client -> client.geolocation().current()); }
            }
            """;

    private Fixtures() {
    }

    /// Compiles `sources` (class name to source) into `classes`; returns javac's diagnostics on failure.
    static void compile(Path classes, Map<String, String> sources, Path... extraClasspath) throws IOException {
        Files.createDirectories(classes);
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new DiagnosticCollector<JavaFileObject>();
        final var units = sources.entrySet().stream().<JavaFileObject>map(entry -> new SimpleJavaFileObject(
                java.net.URI.create("string:///" + entry.getKey().replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return entry.getValue();
            }
        }).toList();
        final var classpath = new StringBuilder(CORE_JAR.toString());
        for (final var extra : extraClasspath) {
            classpath.append(java.io.File.pathSeparator).append(extra);
        }
        final var options = List.of("-d", classes.toString(), "-cp", classpath.toString(), "-parameters", "--add-modules", "org.graalvm.webimage.api");
        final var ok = compiler.getTask(null, null, diagnostics, graalvm() ? options : options.subList(0, options.size() - 2), null, units).call();
        if (!ok) throw new IllegalStateException("fixture compilation failed: " + diagnostics.getDiagnostics());
    }

    static boolean graalvm() {
        return System.getProperty("java.vendor.version", "").contains("GraalVM");
    }
}
