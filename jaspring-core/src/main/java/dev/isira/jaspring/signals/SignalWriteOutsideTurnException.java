package dev.isira.jaspring.signals;

/// Thrown when a signal owned by a page is written from a thread that is not running that page's turn.
/// Fork the work through the zone, or move the write into a continuation.
public final class SignalWriteOutsideTurnException extends IllegalStateException {

    public SignalWriteOutsideTurnException(SignalOwner owner) {
        super("Signal written outside of its owner's turn (" + owner.describe()
                + "); run the write inside zone.run or a fork continuation");
    }
}
