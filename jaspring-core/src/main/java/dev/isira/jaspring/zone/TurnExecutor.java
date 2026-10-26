package dev.isira.jaspring.zone;

import java.util.function.Supplier;

/// Runs work as a page turn: under the page lock, inside the zone, followed by change detection.
/// The live page implements it; the zone uses it to deliver forked results.
public interface TurnExecutor {

    <T> T runTurn(Supplier<T> body);
}
