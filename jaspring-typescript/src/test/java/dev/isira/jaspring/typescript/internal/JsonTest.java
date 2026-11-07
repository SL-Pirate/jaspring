package dev.isira.jaspring.typescript.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void roundTripsTheWireFormat() {
        final var request = new LinkedHashMap<String, Object>();
        request.put("op", "compile");
        request.put("options", Map.of("typeCheck", true));
        request.put("files", List.of(Map.of("path", "a.ts", "text", "const s = \"q\\\"\\n\\t\u0001é\";")));
        request.put("none", null);
        request.put("n", 2322L);
        request.put("d", 1.5);
        final var json = Json.write(request);
        assertEquals("{\"op\":\"compile\",\"options\":{\"typeCheck\":true},\"files\":[{\"path\":\"a.ts\",\"text\":\"const s = \\\"q\\\\\\\"\\\\n\\\\t\\u0001é\\\";\"}],\"none\":null,\"n\":2322,\"d\":1.5}",
                json.replace("\"text\":\"const s = \\\"q\\\\\\\"\\\\n\\\\t\\u0001é\\\";\",\"path\":\"a.ts\"", "\"path\":\"a.ts\",\"text\":\"const s = \\\"q\\\\\\\"\\\\n\\\\t\\u0001é\\\";\""));
        assertEquals(request, Json.parse(json));
    }

    @Test
    void parsesWhitespaceEscapesAndNumbers() {
        assertEquals(Map.of("a", List.of(1L, -2.5, 3.0E2, "é\n\"", true, false)),
                Json.parse(" { \"a\" : [ 1 , -2.5, 3e2, \"\\u00e9\\n\\\"\", true, false ] } "));
        assertEquals(List.of(), Json.parse("[]"));
        assertEquals(Map.of(), Json.parse("{}"));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,]"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} x"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"open"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("nul"));
    }
}
