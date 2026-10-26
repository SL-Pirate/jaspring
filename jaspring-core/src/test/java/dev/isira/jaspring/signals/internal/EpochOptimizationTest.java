package dev.isira.jaspring.signals.internal;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EpochOptimizationTest {

    @Test
    void nonLiveNodeIsNotPolledAgainWhenNoSignalChangedSinceItWasClean() {
        final var unrelated = signal(0);
        final var source = signal(1);
        final var computations = new int[1];
        final var derived = computed(() -> {
            computations[0]++;
            return source.get() * 2;
        });

        assertEquals(2, derived.get());
        assertEquals(1, computations[0]);

        // Forcing the version would make a poll recompute; the epoch shortcut must skip the poll entirely.
        ((ReactiveNode) source).version++;
        assertEquals(2, derived.get());
        assertEquals(1, computations[0]);

        unrelated.set(1);
        assertEquals(2, derived.get());
        assertEquals(2, computations[0]);
    }
}
