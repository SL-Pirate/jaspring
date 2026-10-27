package dev.isira.jaspring.gradle.browser;

import java.util.List;

/// Writes `jaspring-browser.json`, the manifest `dev.isira.jaspring.browser.ClosureManifest` reads at runtime.
/// JSON is written by hand: the plugin has no dependencies.
public final class ManifestWriter {

    public record Asset(String name, String sha256, long size) {}

    private ManifestWriter() {
    }

    public static String write(List<FoundClosure> closures, Asset js, Asset wasm, String graalvm) {
        final var out = new StringBuilder("{\n");
        out.append("  \"version\": 1,\n");
        out.append("  \"graalvm\": ").append(quote(graalvm)).append(",\n");
        out.append("  \"assets\": {");
        if (js == null || wasm == null) {
            out.append("},\n");
        } else {
            out.append("\n    \"js\": ").append(asset(js)).append(",\n    \"wasm\": ").append(asset(wasm)).append("\n  },\n");
        }
        out.append("  \"closures\": [");
        for (var i = 0; i < closures.size(); i++) {
            final var closure = closures.get(i);
            out.append(i == 0 ? "\n" : ",\n");
            out.append("    {\"id\": ").append(quote(closure.id()));
            out.append(", \"implClass\": ").append(quote(closure.implClass()));
            out.append(", \"implMethod\": ").append(quote(closure.implMethod()));
            out.append(", \"implSignature\": ").append(quote(closure.implSignature()));
            out.append(", \"captured\": [");
            for (var c = 0; c < closure.captured().size(); c++) {
                out.append(c == 0 ? "" : ", ").append(quote(closure.captured().get(c).sourceName()));
            }
            out.append("], \"returnType\": ").append(quote(closure.returnType()));
            out.append(", \"promise\": ").append(closure.promise());
            out.append(", \"timing\": ").append(quote(closure.timing().name()));
            out.append(", \"enclosingMethod\": ").append(quote(closure.enclosingMethod()));
            out.append(", \"classSha256\": ").append(quote(closure.classSha256())).append("}");
        }
        out.append(closures.isEmpty() ? "]\n" : "\n  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static String asset(Asset asset) {
        return "{\"name\": " + quote(asset.name()) + ", \"sha256\": " + quote(asset.sha256()) + ", \"size\": " + asset.size() + "}";
    }

    static String quote(String text) {
        if (text == null) return "null";
        final var out = new StringBuilder("\"");
        for (final var c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
