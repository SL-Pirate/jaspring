package dev.isira.jaspring.live;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dev.isira.jaspring.browser.BrowserCallException;
import dev.isira.jaspring.zone.TurnExecutor;
import dev.isira.jaspring.zone.Zone;
import dev.isira.jaspring.zone.ZoneTask;

/// The server-to-browser calls of one page that still wait for their result. Every method runs under the page
/// lock; the timeout thread re-enters through a turn.
///
/// A call made during the initial render is deferred: it travels with the document, but only counts as pending
/// work once the render has finished, because the browser cannot answer before it has the HTML.
final class BrowserCalls {

    /// Longer than the shim's reconnect backoff (10 s), shorter than the page TTL (60 s).
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("jaspring-call-timeout").factory());

    private final Zone zone;
    private final TurnExecutor turns;
    private final Duration timeout;
    private final Map<String, Outstanding> outstanding = new LinkedHashMap<>();
    private final List<BrowserCall> outgoing = new ArrayList<>();
    private final List<String> deferred = new ArrayList<>();
    private boolean deferring;
    private long sequence;

    BrowserCalls(Zone zone, TurnExecutor turns, Duration timeout) {
        this.zone = zone;
        this.turns = turns;
        this.timeout = timeout;
    }

    <T> ZoneTask<T> issue(String target, Long viewId, List<Object> args) {
        final var id = Long.toString(++sequence);
        final var task = zone.<T>task();
        final var call = new Outstanding(task);
        outstanding.put(id, call);
        outgoing.add(new BrowserCall(id, target, viewId, args));
        if (deferring) {
            deferred.add(id);
        } else {
            call.release = zone.pendingWork();
        }
        call.timer = TIMER.schedule(() -> expire(id, target), timeout.toMillis(), TimeUnit.MILLISECONDS);
        return task;
    }

    /// Calls issued since the last drain, in issue order.
    List<BrowserCall> drainOutgoing() {
        if (outgoing.isEmpty()) return List.of();
        final var drained = List.copyOf(outgoing);
        outgoing.clear();
        return drained;
    }

    void beginDeferring() {
        deferring = true;
    }

    /// Turns the calls deferred during the initial render into pending work.
    void activateDeferred() {
        deferring = false;
        for (final var id : deferred) {
            final var call = outstanding.get(id);
            if (call != null) call.release = zone.pendingWork();
        }
        deferred.clear();
    }

    /// Completes the call inside the current turn.
    void settle(BrowserResult result) {
        final var call = outstanding.remove(result.callId());
        if (call == null) throw new IllegalArgumentException("Unknown browser call " + result.callId());
        call.timer.cancel(false);
        call.release.run();
        if (result.error() != null) {
            call.task.completeExceptionally(new BrowserCallException(result.error()));
        } else {
            call.task.complete(result.value());
        }
    }

    /// Releases every call without completing it normally; continuations observe a cancellation.
    void cancelAll() {
        for (final var call : outstanding.values()) {
            call.timer.cancel(false);
            call.release.run();
            call.task.cancel(false);
        }
        outstanding.clear();
        deferred.clear();
        outgoing.clear();
    }

    int outstanding() {
        return outstanding.size();
    }

    private void expire(String id, String target) {
        try {
            turns.runTurn(() -> {
                final var call = outstanding.remove(id);
                if (call != null) {
                    call.release.run();
                    call.task.completeExceptionally(new BrowserCallException(
                            "Browser call '" + target + "' timed out after " + timeout.toSeconds() + "s"));
                }
                return null;
            });
        } catch (PageGoneException ignored) {
            // The page was destroyed; cancelAll released the call already.
        }
    }

    private static final class Outstanding {
        final ZoneTask<Object> task;
        Runnable release = () -> { };
        ScheduledFuture<?> timer;

        @SuppressWarnings("unchecked")
        Outstanding(ZoneTask<?> task) {
            this.task = (ZoneTask<Object>) task;
        }
    }
}
