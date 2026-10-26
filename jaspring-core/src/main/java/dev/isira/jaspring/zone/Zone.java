package dev.isira.jaspring.zone;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.isira.jaspring.context.CurrentContext;

/// Angular's `NgZone` for the server. A turn is any work run through `run`; forked work runs on a virtual thread
/// outside the zone and its completion is delivered as a new turn. The page is stable when no turn is running
/// and no forked task is pending; `whenStable` waits for that.
///
/// Unlike zone.js nothing is patched: only `run`, `fork`, `wrap` and page turns count as "inside the zone".
public final class Zone {

    public enum Mode { ZONE, ZONELESS }

    private static final CurrentContext<Zone> CURRENT = CurrentContext.threadLocal();

    private final Mode mode;
    private final TurnExecutor turns;
    private final ReentrantLock lock;
    private final Condition stableCondition;
    private final PendingTasks pending;
    private final List<Runnable> onTurnStart = new ArrayList<>();
    private final List<Runnable> onTurnDone = new ArrayList<>();
    private final List<Runnable> onStable = new ArrayList<>();
    private final List<Consumer<Throwable>> onError = new ArrayList<>();
    private int nesting;
    private boolean stable = true;

    public Zone(Mode mode, TurnExecutor turns, ReentrantLock lock) {
        this.mode = mode;
        this.turns = turns;
        this.lock = lock;
        this.stableCondition = lock.newCondition();
        this.pending = new PendingTasks(() -> { });
    }

    public static Zone current() {
        final var zone = CURRENT.get();
        if (zone == null) throw new IllegalStateException("Not inside a Jaspring zone");
        return zone;
    }

    public static Zone currentOrNull() {
        return CURRENT.get();
    }

    public static boolean isInZone() {
        return CURRENT.get() != null;
    }

    public Mode mode() {
        return mode;
    }

    public PendingTasks pendingTasks() {
        return pending;
    }

    public <T> T run(Supplier<T> body) {
        enter();
        try {
            return CURRENT.runWith(this, body);
        } catch (RuntimeException | Error e) {
            onError.forEach(handler -> handler.accept(e));
            throw e;
        } finally {
            leave();
        }
    }

    public void run(Runnable body) {
        run(() -> {
            body.run();
            return null;
        });
    }

    public <T> T runOutside(Supplier<T> body) {
        return CURRENT.runWith(null, body);
    }

    public void runOutside(Runnable body) {
        CURRENT.runWith(null, body);
    }

    /// Captures this zone so the runnable re-enters it wherever it is invoked, as a turn.
    public Runnable wrap(Runnable body) {
        return () -> turns.runTurn(() -> {
            body.run();
            return null;
        });
    }

    /// Runs `work` on a virtual thread outside the zone. The result is delivered as a turn, so continuations
    /// may write signals and trigger change detection. The page is not stable until it completes.
    public <T> ZoneTask<T> fork(Callable<T> work) {
        final var task = new ZoneTask<T>(command -> turns.runTurn(() -> {
            command.run();
            return null;
        }));
        final var done = pending.add();
        markUnstable();
        Thread.ofVirtual().name("jaspring-fork").start(() -> {
            T result = null;
            Throwable failure = null;
            try {
                result = CURRENT.runWith(null, () -> {
                    try {
                        return work.call();
                    } catch (Exception e) {
                        throw new ForkFailure(e);
                    }
                });
            } catch (ForkFailure e) {
                failure = e.getCause();
            } catch (Throwable t) {
                failure = t;
            }
            final var outcome = result;
            final var error = failure;
            turns.runTurn(() -> {
                done.run();
                if (error != null) task.completeExceptionally(error); else task.complete(outcome);
                return null;
            });
        });
        return task;
    }

    public ZoneTask<Void> fork(Runnable work) {
        return fork(() -> {
            work.run();
            return null;
        });
    }

    public boolean isStable() {
        return stable;
    }

    /// Waits until no turn is running and no forked task is pending. Must be called holding the page lock,
    /// which it releases while waiting so completions can run their turns.
    public boolean whenStable(Duration timeout) throws InterruptedException {
        var remaining = timeout.toNanos();
        while (!stable) {
            if (remaining <= 0) return false;
            remaining = stableCondition.awaitNanos(remaining);
        }
        return true;
    }

    public void onTurnStart(Runnable hook) {
        onTurnStart.add(hook);
    }

    public void onTurnDone(Runnable hook) {
        onTurnDone.add(hook);
    }

    public void onStable(Runnable hook) {
        onStable.add(hook);
    }

    public void onError(Consumer<Throwable> hook) {
        onError.add(hook);
    }

    private void enter() {
        nesting++;
        if (nesting == 1) {
            markUnstable();
        }
    }

    private void markUnstable() {
        if (stable) {
            stable = false;
            onTurnStart.forEach(Runnable::run);
        }
    }

    private void leave() {
        nesting--;
        if (nesting == 0) {
            onTurnDone.forEach(Runnable::run);
        }
    }

    /// Called by the page at the end of each turn, after change detection, while holding the lock.
    public void checkStable() {
        if (nesting == 0 && !pending.hasPending() && !stable) {
            stable = true;
            onStable.forEach(Runnable::run);
            if (lock.isHeldByCurrentThread()) stableCondition.signalAll();
        }
    }

    private static final class ForkFailure extends RuntimeException {
        ForkFailure(Exception cause) {
            super(cause);
        }
    }
}
