package dev.isira.jaspring.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ClosureIdsTest {

    /// Pinned vector shared with the Gradle plugin's scanner test: both sides must derive this id.
    static final String VECTOR_ID = ClosureIds.of("com/example/GeoComponent", "lambda$locate$0",
            "(Ljava/lang/String;Ldev/isira/jaspring/browser/BrowserClient;)Ljava/lang/String;");

    @Test
    void idIsSixteenHexCharactersDerivedFromClassMethodAndDescriptor() {
        assertEquals(16, VECTOR_ID.length());
        assertEquals(VECTOR_ID, ClosureIds.of("com.example.GeoComponent", "lambda$locate$0",
                "(Ljava/lang/String;Ldev/isira/jaspring/browser/BrowserClient;)Ljava/lang/String;"), "dotted and slashed class names agree");
        assertNotEquals(VECTOR_ID, ClosureIds.of("com/example/GeoComponent", "lambda$locate$1",
                "(Ljava/lang/String;Ldev/isira/jaspring/browser/BrowserClient;)Ljava/lang/String;"));
        assertNotEquals(VECTOR_ID, ClosureIds.of("com/example/GeoComponent", "lambda$locate$0",
                "(Ldev/isira/jaspring/browser/BrowserClient;)Ljava/lang/String;"));
    }

    @Test
    void vectorIsStable() {
        // Changing the formula changes every manifest in the wild; keep this value in sync with the plugin test.
        assertEquals("1cea62e377822423", VECTOR_ID);
    }
}
