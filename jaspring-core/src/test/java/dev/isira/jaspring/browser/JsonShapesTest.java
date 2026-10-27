package dev.isira.jaspring.browser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class JsonShapesTest {

    enum Kind { CITY, TOWN }

    record Tag(String name, Optional<Integer> weight) {}

    record Spot(String city, int zoom, Kind kind, List<Tag> tags, Map<String, Double> scores, Long missing) {}

    @Test
    void convertsNestedRecordsListsMapsOptionalsEnumsAndNumbers() {
        final var json = Map.of("city", "Colombo", "zoom", 3L, "kind", "CITY",
                "tags", List.of(Map.of("name", "beach", "weight", 2), Map.of("name", "food")),
                "scores", Map.of("food", 9, "sun", 8.5));

        final Spot spot = JsonShapes.convert(json, Spot.class);

        assertEquals("Colombo", spot.city());
        assertEquals(3, spot.zoom());
        assertEquals(Kind.CITY, spot.kind());
        assertEquals(List.of(new Tag("beach", Optional.of(2)), new Tag("food", Optional.empty())), spot.tags());
        assertEquals(Map.of("food", 9.0, "sun", 8.5), spot.scores());
        assertEquals(null, spot.missing());
    }

    @Test
    void convertsScalarsAndArrays() {
        assertEquals(7, JsonShapes.<Integer>convert(7.0, int.class));
        assertEquals("x", JsonShapes.<String>convert("x", String.class));
        assertEquals(true, JsonShapes.<Boolean>convert(true, Boolean.class));
        assertArrayEquals(new int[] {1, 2}, JsonShapes.<int[]>convert(List.of(1, 2), int[].class));
        assertEquals(null, JsonShapes.<Object>convert(null, Void.class));
        assertThrows(IllegalArgumentException.class, () -> JsonShapes.convert(null, int.class));
    }

    @Test
    void rejectsTypesThatCannotCrossTheBoundary() {
        assertTrue(JsonShapes.rejection(Spot.class, true).isEmpty());
        assertTrue(JsonShapes.rejection(int[].class, true).isEmpty());
        assertTrue(JsonShapes.rejection(Kind.class, true).isEmpty());
        assertTrue(JsonShapes.rejection(List.class, true).orElseThrow().contains("declare a record"));
        assertTrue(JsonShapes.rejection(Object.class, true).orElseThrow().contains("use a record"));
        assertTrue(JsonShapes.rejection(java.time.LocalDate.class, true).isPresent());
    }
}
