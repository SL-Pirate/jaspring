package dev.isira.jaspring.signals;

import java.util.function.Supplier;

import dev.isira.jaspring.context.CurrentContext;

/// The scheduler that `Signals.effect` registers new effects with. Pages and component views bind it
/// for the duration of a turn; outside of one, effects need an explicit scheduler.
public final class EffectContext {

    private static final CurrentContext<EffectScheduler> CURRENT = CurrentContext.threadLocal();

    private EffectContext() {}

    public static EffectScheduler current() {
        return CURRENT.get();
    }

    public static <T> T runWith(EffectScheduler scheduler, Supplier<T> body) {
        return CURRENT.runWith(scheduler, body);
    }

    public static void runWith(EffectScheduler scheduler, Runnable body) {
        CURRENT.runWith(scheduler, body);
    }

    static EffectScheduler require() {
        final var scheduler = CURRENT.get();
        if (scheduler == null) {
            throw new IllegalStateException(
                    "effect() needs a scheduler: create effects inside a component or pass one explicitly");
        }
        return scheduler;
    }
}
