package dev.isira.jaspring.gradle.jnpm;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Real npm against a small in-process registry. No public registry or credentials are needed by these tests.
class JnpmInstallTest {
    @TempDir Path project;

    @Test void groovyDslLocksPeersAndReinstallsOfflineWithoutRunningScripts() throws Exception {
        try (var registry = new Registry()) {
            configure(registry, false);
            final var locked = runner("jnpmLock", "jnpmInstall").build();
            assertEquals(TaskOutcome.SUCCESS, locked.task(":jnpmInstall").getOutcome());
            final var lock = Files.readString(project.resolve("jnpm.lock"));
            assertTrue(lock.contains("node_modules/peer"));
            assertTrue(Files.exists(project.resolve("build/node_modules/example/package.json")));
            assertFalse(Files.exists(project.resolve("build/node_modules/example/script-ran")));
            assertFalse(Files.exists(project.resolve("package.json")));
            assertFalse(lock.contains(Registry.TOKEN));
            assertFalse(locked.getOutput().contains(Registry.TOKEN));
            assertTrue(registry.authorized.get() > 0);
            registry.server.stop(0);
            runner("clean", "jnpmInstall", "--offline").build();
            assertEquals(lock, Files.readString(project.resolve("jnpm.lock")));
            assertTrue(Files.exists(project.resolve("build/node_modules/peer/package.json")));
            assertEquals(TaskOutcome.UP_TO_DATE, runner("jnpmInstall", "--offline").build().task(":jnpmInstall").getOutcome());
        }
    }

    @Test void kotlinDslAndFrozenBuildRejectChangedDeclarations() throws Exception {
        try (var registry = new Registry()) {
            configure(registry, true);
            runner("jnpmLock", "jnpmInstall").build();
            final var lock = Files.readString(project.resolve("jnpm.lock"));
            final var script = project.resolve("build.gradle.kts");
            Files.writeString(script, Files.readString(script).replace("example:^1.0.0", "example:1.0.0"));
            final var failed = runner("jnpmInstall").buildAndFail();
            assertTrue(failed.getOutput().contains("declarations differ from jnpm.lock"), failed.getOutput());
            assertEquals(lock, Files.readString(project.resolve("jnpm.lock")));
            runner("jnpmLock", "jnpmInstall").build();
            assertEquals("1.0.0", JnpmFiles.read(project.resolve("build/node_modules/example/package.json")).get("version").getAsString());
        }
    }

    @Test void missingLocksAndConflictingScopesFailBeforeInstallation() throws Exception {
        try (var registry = new Registry()) {
            configure(registry, false);
            final var failed = runner("jnpmInstall").buildAndFail();
            assertTrue(failed.getOutput().contains("Missing jnpm.lock"), failed.getOutput());
            Files.writeString(project.resolve("build.gradle"), "\njnpm { api('example:1.0.0') }\n", java.nio.file.StandardOpenOption.APPEND);
            assertTrue(runner("generateJnpmManifest").buildAndFail().getOutput().contains("only one JNPM scope"));
        }
    }

    private void configure(Registry registry, boolean kotlin) throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'jnpm-fixture'\n");
        final var toolchain = JnpmFiles.read(Path.of(System.getProperty("jnpm.test.workspace"), "jnpm/toolchain.json"));
        final var node = Path.of(toolchain.get("node").getAsString());
        final var home = (JnpmToolchain.windows() ? node.getParent() : node.getParent().getParent()).toString().replace("\\", "/");
        final var script = kotlin ? """
                plugins { java; id("dev.isira.jaspring") }
                jnpm {
                    implementation("example:^1.0.0")
                    nodeHome.set(file("%s"))
                    lint { enabled.set(false) }
                    registries {
                        create("fixture") {
                            url.set(uri("%s"))
                            token.set(providers.environmentVariable("JNPM_FIXTURE_TOKEN"))
                        }
                    }
                }
                """.formatted(home, registry.url()) : """
                plugins { id 'java'; id 'dev.isira.jaspring' }
                jnpm {
                    implementation('example:^1.0.0')
                    nodeHome.set(file('%s'))
                    lint { enabled.set(false) }
                    registries {
                        fixture {
                            url.set(uri('%s'))
                            token.set(providers.environmentVariable('JNPM_FIXTURE_TOKEN'))
                        }
                    }
                }
                """.formatted(home, registry.url());
        Files.writeString(project.resolve(kotlin ? "build.gradle.kts" : "build.gradle"), script);
    }

    private GradleRunner runner(String... arguments) {
        final var all = new ArrayList<>(List.of(arguments));
        all.add("--stacktrace");
        final var environment = new HashMap<>(System.getenv());
        environment.put("JNPM_FIXTURE_TOKEN", Registry.TOKEN);
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(all).withEnvironment(environment);
    }

    private static final class Registry implements AutoCloseable {
        static final String TOKEN = "fixture-token-not-a-real-secret";
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final Map<String, Map<String, Object>> versions = new HashMap<>();
        final Map<String, byte[]> archives = new HashMap<>();
        final AtomicInteger authorized = new AtomicInteger();

        Registry() throws Exception {
            add("esbuild", JnpmFiles.VERSIONS.getProperty("esbuild"), Map.of());
            add("typescript", JnpmFiles.VERSIONS.getProperty("typescript"), Map.of());
            add("peer", "1.0.0", Map.of());
            add("example", "1.0.0", Map.of("peerDependencies", Map.of("peer", "^1.0.0")));
            add("example", "1.1.0", Map.of("peerDependencies", Map.of("peer", "^1.0.0")));
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                    authorized.incrementAndGet();
                    final var name = exchange.getRequestURI().getPath().substring(1);
                    final byte[] body;
                    if (archives.containsKey(name)) body = archives.get(name);
                    else if (versions.containsKey(name)) {
                        body = JnpmFiles.JSON.toJson(Map.of("name", name, "dist-tags", Map.of("latest", versions.get(name).keySet().stream().sorted().reduce((a, b) -> b).orElseThrow()),
                                "versions", versions.get(name))).getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                    } else { exchange.sendResponseHeaders(404, -1); return; }
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }

        void add(String name, String version, Map<String, Object> additional) throws Exception {
            final var manifest = new TreeMap<String, Object>(additional);
            manifest.putAll(Map.of("name", name, "version", version, "main", "index.js", "scripts",
                    Map.of("postinstall", "node -e \"require('fs').writeFileSync('script-ran','yes')\"")));
            final var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes); var tar = new TarArchiveOutputStream(gzip)) {
                for (final var entry : Map.of("package/package.json", JnpmFiles.JSON.toJson(manifest), "package/index.js", "module.exports = 1;\n").entrySet()) {
                    final var content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    final var file = new TarArchiveEntry(entry.getKey());
                    file.setSize(content.length);
                    tar.putArchiveEntry(file);
                    tar.write(content);
                    tar.closeArchiveEntry();
                }
            }
            final var archive = name + "/-/" + name + "-" + version + ".tgz";
            archives.put(archive, bytes.toByteArray());
            manifest.put("dist", Map.of("tarball", url() + archive, "integrity", "sha512-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes.toByteArray()))));
            versions.computeIfAbsent(name, ignored -> new TreeMap<>()).put(version, manifest);
        }

        @Override public void close() { server.stop(0); }
    }
}
