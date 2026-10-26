package dev.isira.jaspring.signals;

/// Thrown when a signal is written inside a `computed`, which must stay pure.
public final class InvalidSignalWriteException extends IllegalStateException {

    public InvalidSignalWriteException() {
        super("Writing to signals is not allowed in a computed");
    }
}
