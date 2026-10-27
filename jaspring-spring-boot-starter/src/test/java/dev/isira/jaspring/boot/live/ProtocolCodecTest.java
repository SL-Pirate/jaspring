package dev.isira.jaspring.boot.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.live.BrowserCall;
import dev.isira.jaspring.live.Patch;
import dev.isira.jaspring.live.PatchSet;

class ProtocolCodecTest {

    final ProtocolCodec codec = new ProtocolCodec();

    @Test
    void callsFollowPatchesAndCarryTargetViewAndArguments() {
        final var patches = new PatchSet(List.of(new Patch(2, "<b>x</b>")), null,
                List.of(new BrowserCall("7", "spark.size", 2L, List.of(1, "a")), new BrowserCall("8", "viewport", null, List.of())));

        final var messages = codec.messages(patches, 3);

        assertEquals(3, messages.size());
        assertEquals("patch", messages.get(0).get("t"));
        assertEquals("{\"t\":\"call\",\"id\":\"7\",\"target\":\"spark.size\",\"v\":2,\"args\":[1,\"a\"]}", codec.encode(messages.get(1)));
        assertEquals("{\"t\":\"call\",\"id\":\"8\",\"target\":\"viewport\",\"args\":[]}", codec.encode(messages.get(2)));
    }

    @Test
    void decodesResultsWithAValueOrAnError() {
        final var ok = codec.decode("{\"t\":\"result\",\"page\":\"p\",\"id\":\"7\",\"value\":{\"width\":800}}");
        assertEquals("result", ok.type());
        assertEquals("7", ok.id());
        assertEquals(Map.of("width", 800), ok.value());
        assertNull(ok.error());

        final var failed = codec.decode("{\"t\":\"result\",\"page\":\"p\",\"id\":\"7\",\"error\":{\"message\":\"no hook\"}}");
        assertEquals("no hook", failed.error());
        assertNull(failed.value());

        final var event = codec.decode("{\"t\":\"event\",\"page\":\"p\",\"h\":\"1.1\",\"ev\":{\"type\":\"click\"}}");
        assertEquals("1.1", event.handler());
        assertNull(event.id());
    }
}
