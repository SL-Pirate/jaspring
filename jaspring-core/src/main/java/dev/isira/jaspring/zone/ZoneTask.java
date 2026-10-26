package dev.isira.jaspring.zone;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/// The future of a forked task. It completes inside a page turn, so synchronous continuations run under
/// the page lock and may write signals; `*Async` continuations run as their own turns.
public final class ZoneTask<T> extends CompletableFuture<T> {

    private final Executor turnExecutor;

    ZoneTask(Executor turnExecutor) {
        this.turnExecutor = turnExecutor;
    }

    @Override
    public Executor defaultExecutor() {
        return turnExecutor;
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new ZoneTask<>(turnExecutor);
    }
}
