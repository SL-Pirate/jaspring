package dev.isira.jaspring.boot.live;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import dev.isira.jaspring.boot.JaspringRuntime;
import dev.isira.jaspring.live.BrowserResult;
import dev.isira.jaspring.live.EventEnvelope;
import dev.isira.jaspring.live.LivePage;
import jakarta.servlet.http.HttpServletRequest;

/// HTTP transport endpoints. A response is the array of server messages for that turn plus `pending` when
/// forked work is still running, which tells the shim to poll.
@org.springframework.web.bind.annotation.RestController
public class LiveEventController {

    private final JaspringRuntime runtime;
    private final LiveSessions sessions;
    private final ProtocolCodec codec;

    public LiveEventController(JaspringRuntime runtime, LiveSessions sessions, ProtocolCodec codec) {
        this.runtime = runtime;
        this.sessions = sessions;
        this.codec = codec;
    }

    @PostMapping(value = "/_jaspring/event", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> event(@RequestBody String body, HttpServletRequest request) {
        final var message = codec.decode(body);
        final var page = find(message.page(), request);
        if (page == null) return gone();
        final var state = sessions.state(page);
        final var messages = new ArrayList<Map<String, Object>>();
        try {
            final var patches = switch (message.type()) {
                case "event" -> runtime.handleEvent(page, new EventEnvelope(message.handler(), message.event()));
                case "result" -> runtime.handleResult(page, new BrowserResult(message.id(), message.value(), message.error()));
                default -> null;
            };
            if (patches == null) {
                messages.add(codec.error("protocol", "Unknown message type " + message.type()));
            } else {
                messages.addAll(codec.messages(patches, state.nextSeq()));
            }
        } catch (RuntimeException e) {
            messages.add(codec.error("handler", runtime.properties().isDevMode() ? String.valueOf(e.getMessage()) : "Event handler failed"));
        }
        state.queue().drain().forEach(queued -> messages.addAll(codec.messages(queued, state.nextSeq())));
        return ResponseEntity.ok(envelope(messages, !page.zone().isStable()));
    }

    @GetMapping(value = "/_jaspring/poll", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> poll(@RequestParam("page") String pageId, HttpServletRequest request) {
        final var page = find(pageId, request);
        if (page == null) return gone();
        final var state = sessions.state(page);
        final var messages = new ArrayList<Map<String, Object>>();
        state.queue().drain().forEach(queued -> messages.addAll(codec.messages(queued, state.nextSeq())));
        return ResponseEntity.ok(envelope(messages, !page.zone().isStable()));
    }

    private LivePage find(String pageId, HttpServletRequest request) {
        final var session = request.getSession(false);
        if (pageId == null || session == null) return null;
        return runtime.find(pageId, session.getId()).orElse(null);
    }

    private ResponseEntity<String> gone() {
        return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.APPLICATION_JSON)
                .body(envelope(List.of(codec.error("unknown-page", "This page is no longer live; reload")), false));
    }

    private String envelope(List<Map<String, Object>> messages, boolean pending) {
        return codec.encode(Map.of("messages", messages, "pending", pending));
    }
}
