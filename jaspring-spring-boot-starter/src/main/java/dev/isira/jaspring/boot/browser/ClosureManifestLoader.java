package dev.isira.jaspring.boot.browser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;

import dev.isira.jaspring.browser.ClosureManifest;
import tools.jackson.databind.json.JsonMapper;

/// Reads `classpath:/jaspring/jaspring-browser.json`, written by the Gradle plugin's `buildBrowserImage` task.
public final class ClosureManifestLoader {

    public static final String LOCATION = "classpath:/jaspring/jaspring-browser.json";

    private static final Logger log = LoggerFactory.getLogger(ClosureManifestLoader.class);

    private ClosureManifestLoader() {
    }

    public static ClosureManifest load(ResourceLoader loader) {
        final var resource = loader.getResource(LOCATION);
        if (!resource.exists()) return ClosureManifest.EMPTY;
        try (var in = resource.getInputStream()) {
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + LOCATION, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static ClosureManifest parse(String json) {
        final var root = JsonMapper.builder().build().readValue(json, Map.class);
        final var graalvm = (String) root.get("graalvm");
        final var assets = (Map<String, Object>) root.get("assets");
        final var closures = new ArrayList<ClosureManifest.Closure>();
        for (final var entry : (List<Map<String, Object>>) root.getOrDefault("closures", List.of())) {
            closures.add(new ClosureManifest.Closure((String) entry.get("id"), (String) entry.get("implClass"), (String) entry.get("implMethod"),
                    (String) entry.get("implSignature"), (List<String>) entry.getOrDefault("captured", List.of()), (String) entry.get("returnType"),
                    Boolean.TRUE.equals(entry.get("promise")), ClosureManifest.Timing.valueOf((String) entry.get("timing")),
                    (String) entry.get("enclosingMethod"), (String) entry.get("classSha256")));
        }
        final var manifest = new ClosureManifest(graalvm, assets == null || assets.isEmpty() ? null
                : new ClosureManifest.Assets(asset((Map<String, Object>) assets.get("js")), asset((Map<String, Object>) assets.get("wasm"))), closures);
        warnOnToolchainDrift(graalvm);
        return manifest;
    }

    private static ClosureManifest.Asset asset(Map<String, Object> entry) {
        return new ClosureManifest.Asset((String) entry.get("name"), (String) entry.get("sha256"), ((Number) entry.get("size")).longValue());
    }

    /// The image was built with one GraalVM line; running the server on another is fine, but a different Web Image
    /// version is worth a note when something misbehaves.
    private static void warnOnToolchainDrift(String graalvm) {
        final var running = System.getProperty("java.vendor.version", "");
        if (graalvm != null && running.contains("GraalVM") && !graalvm.contains(majorMinor(running))) {
            log.info("Browser closures were built with '{}'; this server runs '{}'", graalvm, running);
        }
    }

    private static String majorMinor(String vendorVersion) {
        final var matcher = java.util.regex.Pattern.compile("(\\d+\\.\\d+)").matcher(vendorVersion);
        return matcher.find() ? matcher.group(1) : vendorVersion;
    }
}
