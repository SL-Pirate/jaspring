package dev.isira.jaspring.boot.live;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.isira.jaspring.live.LivePage;

/// Per-page transport state shared by both transports: the queued sink used while no socket is attached,
/// the patch sequence number, and whether the page has had a client before (reconnects get a full resync).
public final class LiveSessions {

    public static final class State {
        final QueuedPatchSink queue = new QueuedPatchSink();
        final AtomicLong seq = new AtomicLong();
        volatile boolean connectedBefore;

        public QueuedPatchSink queue() {
            return queue;
        }

        public long nextSeq() {
            return seq.incrementAndGet();
        }
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public State state(LivePage page) {
        return states.computeIfAbsent(page.id(), id -> {
            final var state = new State();
            page.sink(state.queue);
            return state;
        });
    }

    public void forget(String pageId) {
        states.remove(pageId);
    }
}
