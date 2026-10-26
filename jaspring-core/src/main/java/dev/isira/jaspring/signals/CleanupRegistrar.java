package dev.isira.jaspring.signals;

/// Passed to an effect body so it can register work to run before the next run or on destroy.
/// Cleanups run in reverse registration order.
@FunctionalInterface
public interface CleanupRegistrar {

    void onCleanup(Runnable cleanup);
}
