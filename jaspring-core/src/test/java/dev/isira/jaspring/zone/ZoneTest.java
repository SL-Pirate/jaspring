package dev.isira.jaspring.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

class ZoneTest {

    /// A page stand-in: turns run under the lock, inside the zone, and check stability afterwards.
    static final class Turns implements TurnExecutor {
        final ReentrantLock lock = new ReentrantLock();
        Zone zone;

        @Override
        public <T> T runTurn(java.util.function.Supplier<T> body) {
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

    static Zone zone(Turns turns) {
        turns.zone = new Zone(Zone.Mode.ZONE, turns, turns.lock);
        return turns.zone;
    }

    @Test
    void pendingWorkKeepsTheZoneUnstableUntilReleased() {
        final var turns = new Turns();
        final var zone = zone(turns);

        final var release = turns.runTurn(zone::pendingWork);
        assertFalse(zone.isStable());
        assertEquals(1, zone.pendingTasks().count());

        turns.runTurn(() -> {
            release.run();
            return null;
        });
        assertTrue(zone.isStable());
        assertEquals(0, zone.pendingTasks().count());
    }

    @Test
    void tasksDeliverContinuationsAsTurns() {
        final var turns = new Turns();
        final var zone = zone(turns);
        final var task = zone.<String>task();
        final var seenInTurn = new boolean[1];

        task.thenAcceptAsync(value -> seenInTurn[0] = Zone.isInZone() && turns.lock.isHeldByCurrentThread());
        turns.runTurn(() -> task.complete("x"));

        assertTrue(task.isDone());
        assertTrue(seenInTurn[0], "async continuation ran as a turn");
    }
}
