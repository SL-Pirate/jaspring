package dev.isira.jaspring.boot.live;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import dev.isira.jaspring.boot.JaspringRuntime;
import dev.isira.jaspring.live.EventEnvelope;
import dev.isira.jaspring.live.LivePage;
import dev.isira.jaspring.live.PatchSet;

/// One socket per page. `join` attaches the socket as the page's patch sink (a reconnect gets a full resync),
/// `event` runs a turn and sends its patches, and pushes from forked work go out as they happen.
public class JaspringEventHandler extends TextWebSocketHandler {

    private static final String SESSION_ID = "HTTP.SESSION.ID";

    private final JaspringRuntime runtime;
    private final LiveSessions sessions;
    private final ProtocolCodec codec;
    private final Map<String, Attached> attached = new ConcurrentHashMap<>();

    private record Attached(WebSocketSession socket, LivePage page) {}

    public JaspringEventHandler(JaspringRuntime runtime, LiveSessions sessions, ProtocolCodec codec) {
        this.runtime = runtime;
        this.sessions = sessions;
        this.codec = codec;
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) throws Exception {
        final var socket = attached.containsKey(rawSession.getId()) ? attached.get(rawSession.getId()).socket() : decorate(rawSession);
        final var client = codec.decode(message.getPayload());
        switch (client.type()) {
            case "join" -> join(socket, client.page());
            case "event" -> event(socket, client);
            case "pong" -> { }
            default -> send(socket, codec.error("protocol", "Unknown message type " + client.type()));
        }
    }

    private void join(WebSocketSession socket, String pageId) throws IOException {
        final var page = find(socket, pageId);
        if (page == null) {
            send(socket, codec.error("unknown-page", "This page is no longer live; reload"));
            return;
        }
        final var state = sessions.state(page);
        attached.put(socket.getId(), new Attached(socket, page));
        runtime.pages().connected(pageId);
        page.sink(patches -> sendPatches(socket, state, patches));
        if (state.connectedBefore) {
            sendPatches(socket, state, page.renderFull());
        }
        state.connectedBefore = true;
        state.queue().drain().forEach(queued -> sendPatches(socket, state, queued));
    }

    private void event(WebSocketSession socket, ProtocolCodec.ClientMessage client) throws IOException {
        final var page = find(socket, client.page());
        if (page == null) {
            send(socket, codec.error("unknown-page", "This page is no longer live; reload"));
            return;
        }
        final var state = sessions.state(page);
        try {
            sendPatches(socket, state, runtime.handleEvent(page, new EventEnvelope(client.handler(), client.event())));
        } catch (RuntimeException e) {
            send(socket, codec.error("handler", runtime.properties().isDevMode() ? String.valueOf(e.getMessage()) : "Event handler failed"));
        }
    }

    private LivePage find(WebSocketSession socket, String pageId) {
        final var sessionId = socket.getAttributes().get(SESSION_ID);
        if (pageId == null || sessionId == null) return null;
        return runtime.find(pageId, sessionId.toString()).orElse(null);
    }

    private void sendPatches(WebSocketSession socket, LiveSessions.State state, PatchSet patches) {
        for (final var message : codec.messages(patches, state.nextSeq())) {
            try {
                send(socket, message);
            } catch (IOException e) {
                // The socket is gone; the registry will evict the page after the TTL.
                return;
            }
        }
    }

    private void send(WebSocketSession socket, Map<String, Object> message) throws IOException {
        if (socket.isOpen()) socket.sendMessage(new TextMessage(codec.encode(message)));
    }

    private static WebSocketSession decorate(WebSocketSession session) {
        return new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        final var entry = attached.remove(session.getId());
        if (entry == null) return;
        final var state = sessions.state(entry.page());
        entry.page().sink(state.queue());
        runtime.pages().disconnected(entry.page().id());
    }
}
