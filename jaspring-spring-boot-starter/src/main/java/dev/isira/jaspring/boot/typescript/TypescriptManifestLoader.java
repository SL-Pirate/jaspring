package dev.isira.jaspring.boot.typescript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ResourceLoader;

import tools.jackson.databind.json.JsonMapper;

/// Reads `classpath:/jaspring/jaspring-typescript.json`, written by the Gradle plugin's `compileTypescript` task.
public final class TypescriptManifestLoader {

    public static final String LOCATION = "classpath:/jaspring/jaspring-typescript.json";

    private TypescriptManifestLoader() {
    }

    public static TypescriptScripts load(ResourceLoader loader) {
        final var resource = loader.getResource(LOCATION);
        if (!resource.exists()) return TypescriptScripts.NONE;
        try (var in = resource.getInputStream()) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + LOCATION, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static TypescriptScripts parse(String json) {
        final var root = JsonMapper.builder().build().readValue(json, Map.class);
        final var version = ((Number) root.getOrDefault("version", 1)).intValue();
        if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported Jaspring TypeScript manifest version: " + version);
        final var scripts = new ArrayList<TypescriptScripts.Script>();
        for (final var entry : (List<Map<String, Object>>) root.getOrDefault("files", List.of())) {
            scripts.add(new TypescriptScripts.Script(safePath((String) entry.get("source")), (String) entry.get("sha256"), safePath((String) entry.get("output")), null));
        }
        if (version == 1) return new TypescriptScripts((String) root.get("typescript"), scripts);
        final var entries = ((List<String>) root.getOrDefault("entries", List.of())).stream().map(TypescriptManifestLoader::safePath).toList();
        final var styles = ((List<String>) root.getOrDefault("styles", List.of())).stream().map(TypescriptManifestLoader::safePath).toList();
        final var inputs = (Map<String, String>) root.getOrDefault("inputs", Map.of());
        inputs.keySet().forEach(TypescriptManifestLoader::safePath);
        return new TypescriptScripts((String) root.get("typescript"), scripts, true, entries, styles, (String) root.get("lockSha256"), inputs);
    }

    private static String safePath(String path) {
        if (path == null) return null;
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains("\"") || path.contains("<") || path.contains("?") || path.contains("#")
                || java.util.Arrays.stream(path.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
            throw new IllegalArgumentException("Invalid TypeScript manifest path: " + path);
        }
        return path;
    }
}
