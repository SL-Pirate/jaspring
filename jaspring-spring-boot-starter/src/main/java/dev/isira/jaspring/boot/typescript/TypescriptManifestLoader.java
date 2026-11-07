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
        final var scripts = new ArrayList<TypescriptScripts.Script>();
        for (final var entry : (List<Map<String, Object>>) root.getOrDefault("files", List.of())) {
            scripts.add(new TypescriptScripts.Script((String) entry.get("source"), (String) entry.get("sha256"), (String) entry.get("output"), null));
        }
        return new TypescriptScripts((String) root.get("typescript"), scripts);
    }
}
