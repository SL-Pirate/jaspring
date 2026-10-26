package dev.isira.jaspring.pipe.builtin;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.signals.Signal;
import dev.isira.jaspring.template.eval.Js;

/// `value | async`: reads a signal, or waits for a `CompletionStage`/`Future` on the (virtual) rendering thread.
/// There is no streaming on the server; a page renders when its data has arrived.
final class AsyncPipe implements PipeTransform {

    @Override
    public Object transform(Object value, Object... args) {
        return switch (value) {
            case null -> null;
            case Signal<?> signal -> signal.get();
            case CompletionStage<?> stage -> stage.toCompletableFuture().join();
            case Future<?> future -> await(future);
            default -> {
                if (Js.isNullish(value)) yield null;
                throw new IllegalArgumentException("async pipe expects a Signal, CompletionStage or Future, got " + value.getClass().getSimpleName());
            }
        };
    }

    private static Object await(Future<?> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the async value", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Async value failed: " + e.getCause().getMessage(), e.getCause());
        }
    }
}
