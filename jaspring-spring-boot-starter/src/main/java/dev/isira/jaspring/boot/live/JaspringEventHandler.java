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
import dev.isira.jaspring.live.BrowserResult;
import dev.isira.jaspring.live.EventEnvelope;
import dev.isira.jaspring.live.LivePage;
import dev.isira.jaspring.live.PatchSet;

/// One socket per page. `join` attaches the socket as the page's patch sink (a reconnect gets a full resync),
/// `event` runs a turn and sends its patches, and pushes from forked work go out as they happen.
///
/// Incoming messages are taken as fragments and joined here, so the container's text message buffer (8 KB on
/// Tomcat) does not limit them and the application's other WebSocket endpoints keep their own settings.
public class JaspringEventHandler extends TextWebSocketHandler {

    /// Largest client message, in characters. The shim answers a larger call result with an error instead of
    /// sending it; anything else above this closes the socket.
    static final int MAX_MESSAGE_CHARS = 4 * 1024 * 1024;

    private static final String SESSION_ID = "HTTP.SESSION.ID";

    private final JaspringRuntime runtime;
    private final LiveSessions sessions;
    private final ProtocolCodec codec;
    private final Map<String, Attached> attached = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> fragments = new ConcurrentHashMap<>();

    private record Attached(WebSocketSession socket, LivePage page) {}

    public JaspringEventHandler(JaspringRuntime runtime, LiveSessions sessions, ProtocolCodec codec) {
        this.runtime = runtime;
        this.sessions = sessions;
        this.codec = codec;
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage fragment) throws Exception {
        final var buffer = fragments.computeIfAbsent(rawSession.getId(), id -> new StringBuilder());
        if (buffer.length() + fragment.getPayloadLength() > MAX_MESSAGE_CHARS) {
            fragments.remove(rawSession.getId());
            rawSession.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        buffer.append(fragment.getPayload());
        if (!fragment.isLast()) return;
        fragments.remove(rawSession.getId());
        handleWholeMessage(rawSession, buffer.toString());
    }

    private void handleWholeMessage(WebSocketSession rawSession, String payload) throws IOException {
        final var socket = attached.containsKey(rawSession.getId()) ? attached.get(rawSession.getId()).socket() : decorate(rawSession);
        final var client = codec.decode(payload);
        switch (client.type()) {
            case "join" -> join(socket, client.page());
            case "event" -> turn(socket, client, page -> runtime.handleEvent(page, new EventEnvelope(client.handler(), client.event())));
            case "result" -> turn(socket, client, page -> runtime.handleResult(page, new BrowserResult(client.id(), client.value(), client.error())));
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

    private void turn(WebSocketSession socket, ProtocolCodec.ClientMessage client, java.util.function.Function<LivePage, PatchSet> body)
            throws IOException {
        final var page = find(socket, client.page());
        if (page == null) {
            send(socket, codec.error("unknown-page", "This page is no longer live; reload"));
            return;
        }
        final var state = sessions.state(page);
        try {
            sendPatches(socket, state, body.apply(page));
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
        // Room for several large patches queued behind a slow send; going over the limit drops the socket.
        return new ConcurrentWebSocketSessionDecorator(session, 10_000, 16 * 1024 * 1024);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        fragments.remove(session.getId());
        final var entry = attached.remove(session.getId());
        if (entry == null) return;
        final var state = sessions.state(entry.page());
        entry.page().sink(state.queue());
        runtime.pages().disconnected(entry.page().id());
    }
}
