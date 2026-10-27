package dev.isira.jaspring.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.browser.BrowserCallException;
import dev.isira.jaspring.zone.TurnExecutor;
import dev.isira.jaspring.zone.Zone;

class BrowserCallsTest {

    static final class Turns implements TurnExecutor {
        final ReentrantLock lock = new ReentrantLock();
        Zone zone;

        @Override
        public <T> T runTurn(Supplier<T> body) {
            lock.lock();
            try {
                final var result = zone.run(body);
                zone.checkStable();
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    void callsTimeOutAndReleaseThePendingWork() throws Exception {
        final var turns = new Turns();
        turns.zone = new Zone(Zone.Mode.ZONE, turns, turns.lock);
        final var calls = new BrowserCalls(turns.zone, turns, Duration.ofMillis(50));

        final var task = turns.runTurn(() -> calls.<Object>issue("slow", null, List.of()));
        assertFalse(turns.zone.isStable());
        assertEquals(1, calls.drainOutgoing().size());

        final var failure = assertThrows(ExecutionException.class, () -> task.get(2, TimeUnit.SECONDS));
        assertEquals(BrowserCallException.class, failure.getCause().getClass());
        assertTrue(failure.getCause().getMessage().contains("timed out"));
        assertEquals(0, calls.outstanding());
        assertTrue(turns.zone.isStable());
    }

    @Test
    void deferredCallsBecomePendingOnlyWhenActivated() {
        final var turns = new Turns();
        turns.zone = new Zone(Zone.Mode.ZONE, turns, turns.lock);
        final var calls = new BrowserCalls(turns.zone, turns, Duration.ofSeconds(5));

        calls.beginDeferring();
        turns.runTurn(() -> calls.<Object>issue("early", null, List.of()));
        assertTrue(turns.zone.isStable(), "a deferred call does not hold the initial render");

        turns.runTurn(() -> {
            calls.activateDeferred();
            return null;
        });
        assertFalse(turns.zone.isStable());

        turns.runTurn(() -> {
            calls.settle(new BrowserResult("1", "ok", null));
            return null;
        });
        assertTrue(turns.zone.isStable());
    }
}
