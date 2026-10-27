package dev.isira.jaspring.gradle.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ManifestWriterTest {

    @Test
    void writesClosuresAssetsAndEscapes() {
        final var closure = new FoundClosure("1cea62e377822423", "fx.Comp", "lambda$save$9f$0",
                "(Ljava/lang/String;ILdev/isira/jaspring/browser/BrowserClient;)Lfx/Comp$Spot;",
                List.of(new TypeShapes.Scalar("java.lang.String"), new TypeShapes.Scalar("int")),
                new TypeShapes.RecordShape("fx.Comp$Spot", List.of()), false, FoundClosure.Timing.EVENT, "save", "abc");

        final var json = ManifestWriter.write(List.of(closure), new ManifestWriter.Asset("jaspring-app.js", "11", 10),
                new ManifestWriter.Asset("jaspring-app.js.wasm", "22", 20), "Oracle GraalVM 25.2.4 \"x\"");

        assertTrue(json.contains("\"graalvm\": \"Oracle GraalVM 25.2.4 \\\"x\\\"\""), json);
        assertTrue(json.contains("\"js\": {\"name\": \"jaspring-app.js\", \"sha256\": \"11\", \"size\": 10}"), json);
        assertTrue(json.contains("\"id\": \"1cea62e377822423\""), json);
        assertTrue(json.contains("\"captured\": [\"java.lang.String\", \"int\"]"), json);
        assertTrue(json.contains("\"returnType\": \"fx.Comp$Spot\""), json);
        assertTrue(json.contains("\"timing\": \"EVENT\""), json);
        assertEquals("\"a\\nb\\\\c\"", ManifestWriter.quote("a\nb\\c"));
    }

    @Test
    void emptyManifestHasNoAssets() {
        final var json = ManifestWriter.write(List.of(), null, null, null);
        assertTrue(json.contains("\"assets\": {}"), json);
        assertTrue(json.contains("\"closures\": []"), json);
        assertTrue(json.contains("\"graalvm\": null"), json);
    }
}
