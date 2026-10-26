package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlitchFreeTest {

    @Test
    void diamondDependencyRecomputesOnce() {
        final var fullRuns = new int[1];
        final var name = signal("John Doe");
        final var first = computed(() -> name.get().split(" ")[0]);
        final var last = computed(() -> name.get().split(" ")[1]);
        final var full = computed(() -> {
            fullRuns[0]++;
            return first.get() + "/" + last.get();
        });

        assertEquals("John/Doe", full.get());
        assertEquals(1, fullRuns[0]);
        name.set("Bob Fisher");
        assertEquals("Bob/Fisher", full.get());
        assertEquals(2, fullRuns[0]);
    }

    @Test
    void directAndIndirectDependencyRecomputesOnce() {
        final var a = signal("a");
        final var b = computed(() -> a.get() + "b");
        final var cRuns = new int[1];
        final var c = computed(() -> a.get() + "|" + b.get() + "|" + ++cRuns[0]);

        assertEquals("a|ab|1", c.get());
        a.set("A");
        assertEquals("A|Ab|2", c.get());
    }
}
