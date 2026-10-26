package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.effect;
import static dev.isira.jaspring.signals.Signals.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class ComputedTest {

    @Test
    void recomputesOnlyWhenADependencyChanges() {
        final var counter = signal(0);
        final var runs = new int[1];
        final var twice = computed(() -> (counter.get() * 2) + ":" + ++runs[0]);

        assertEquals("0:1", twice.get());
        counter.set(1);
        assertEquals("2:2", twice.get());
        assertEquals("2:2", twice.get());
        counter.set(2);
        assertEquals("4:3", twice.get());
    }

    @Test
    void doesNotRecomputeWithoutDependencies() {
        final var tick = new int[1];
        final var c = computed(() -> ++tick[0]);
        assertEquals(1, c.get());
        assertEquals(1, c.get());
    }

    @Test
    void doesNotRecomputeWhenThePrimitiveDependencyIsUnchanged() {
        final var counter = signal(0);
        final var runs = new int[1];
        final var twice = computed(() -> (counter.get() * 2) + ":" + ++runs[0]);

        assertEquals("0:1", twice.get());
        counter.set(0);
        assertEquals("0:1", twice.get());
    }

    @Test
    void chainsComputeds() {
        final var name = signal("abc");
        final var reverse = computed(() -> new StringBuilder(name.get()).reverse().toString());
        final var upper = computed(() -> reverse.get().toUpperCase());

        assertEquals("CBA", upper.get());
        name.set("foo");
        assertEquals("OOF", upper.get());
    }

    @Test
    void tracksOnlyDependenciesReadInTheLastRun() {
        final var name = signal("John");
        final var age = signal(25);
        final var show = signal(true);
        final var runs = new int[1];
        final var display = computed(() ->
                (show.get() ? name.get() + " aged " + age.get() : "anonymous") + ":" + ++runs[0]);

        assertEquals("John aged 25:1", display.get());
        show.set(false);
        assertEquals("anonymous:2", display.get());
        name.set("Bob");
        assertEquals("anonymous:2", display.get());
    }

    @Test
    void detectsSimpleCycles() {
        final Signal<Object>[] self = new Signal[1];
        self[0] = computed(() -> self[0].get());

        final var error = assertThrows(IllegalStateException.class, () -> self[0].get());
        assertEquals("Detected cycle in computations.", error.getMessage());
    }

    @Test
    void detectsDeepCycles() {
        final Signal<Object>[] a = new Signal[1];
        final var d = computed(() -> a[0].get());
        final var c = computed(d::get);
        final var b = computed(c::get);
        a[0] = computed(b::get);

        final var error = assertThrows(IllegalStateException.class, () -> a[0].get());
        assertEquals("Detected cycle in computations.", error.getMessage());
    }

    @Test
    void cachesExceptionsUntilDirtyAgain() {
        final var toggle = signal("KO");
        final var runs = new int[1];
        final var c = computed(() -> {
            runs[0]++;
            final var value = toggle.get();
            if (value.equals("KO")) throw new IllegalArgumentException("KO");
            return value;
        });

        assertEquals("KO", assertThrows(IllegalArgumentException.class, c::get).getMessage());
        assertEquals("KO", assertThrows(IllegalArgumentException.class, c::get).getMessage());
        assertEquals(1, runs[0]);
        toggle.set("OK");
        assertEquals("OK", c.get());
    }

    @Test
    void doesNotPropagateWhenIntermediateValueIsUnchanged() {
        final var source = signal(0);
        final var isEven = computed(() -> source.get() % 2 == 0);
        final var updates = new int[1];
        final var tracker = computed(() -> {
            isEven.get();
            return updates[0]++;
        });

        tracker.get();
        assertEquals(1, updates[0]);
        source.set(1);
        tracker.get();
        assertEquals(2, updates[0]);
        source.set(3);
        tracker.get();
        assertEquals(2, updates[0]);
        source.set(4);
        tracker.get();
        assertEquals(3, updates[0]);
    }

    @Test
    void doesNotMarkAlreadyDirtyComputedsAgain() {
        final var source = signal("a");
        final var derived = computed(() -> source.get().toUpperCase());
        final var scheduler = new CountingScheduler();
        final var ref = effect(cleanup -> derived.get(), scheduler);
        scheduler.flush();
        final var baseline = scheduler.scheduled;

        source.set("b");
        assertEquals(baseline + 1, scheduler.scheduled);
        source.set("c");
        assertEquals(baseline + 1, scheduler.scheduled);
        scheduler.flush();
        source.set("d");
        assertEquals(baseline + 2, scheduler.scheduled);
        ref.destroy();
    }

    @Test
    void allowsSignalCreationInsideComputed() {
        final var twice = computed(() -> signal(1).get() * 2);
        assertEquals(2, twice.get());
    }

    @Test
    void disallowsSignalWritesInsideComputed() {
        final var source = signal(0);
        final var illegal = computed(() -> {
            source.set(1);
            return 0;
        });
        assertThrows(InvalidSignalWriteException.class, illegal::get);
    }

    @Test
    void cachesExceptionsThrownByEquality() {
        final var s = signal(0);
        final var runs = new int[1];
        final var equalRuns = new int[1];
        final var c = computed(() -> {
            runs[0]++;
            return s.get();
        }, (a, b) -> {
            equalRuns[0]++;
            throw new IllegalStateException("equal");
        });

        assertEquals(0, c.get());
        assertEquals(0, equalRuns[0]);
        s.set(1);
        assertEquals("equal", assertThrows(IllegalStateException.class, c::get).getMessage());
        assertEquals(2, runs[0]);
        assertEquals(1, equalRuns[0]);
        assertEquals("equal", assertThrows(IllegalStateException.class, c::get).getMessage());
        assertEquals(2, runs[0]);
        assertEquals(1, equalRuns[0]);
    }

    @Test
    void doesNotTrackSignalReadsInsideEquality() {
        final var value = signal(1.0);
        final var epsilon = signal(0.5);
        final var innerRuns = new int[1];
        final var equalRuns = new int[1];
        final var inner = computed(() -> {
            innerRuns[0]++;
            return value.get();
        }, (a, b) -> {
            equalRuns[0]++;
            return Math.abs(a - b) < epsilon.get();
        });
        final var outerRuns = new int[1];
        final var outer = computed(() -> {
            outerRuns[0]++;
            return inner.get();
        });

        assertEquals(1.0, outer.get());
        value.set(1.2);
        assertEquals(1.0, outer.get());
        assertEquals(2, innerRuns[0]);
        assertEquals(1, equalRuns[0]);
        assertEquals(1, outerRuns[0]);

        epsilon.set(0.1);
        assertEquals(1.0, outer.get());
        assertEquals(2, innerRuns[0]);
        assertEquals(1, equalRuns[0]);
        assertEquals(1, outerRuns[0]);
    }

    @Test
    void recoversFromEqualityException() {
        final var shouldThrow = new boolean[] {true};
        final var source = signal(0);
        final Supplier<Integer> read = source::get;
        final var derived = computed(read, (a, b) -> {
            if (shouldThrow[0]) throw new IllegalStateException("equal");
            return a.equals(b);
        });

        assertEquals(0, derived.get());
        source.set(1);
        assertThrows(IllegalStateException.class, derived::get);
        shouldThrow[0] = false;
        source.set(2);
        assertEquals(2, derived.get());
    }

    static final class CountingScheduler extends QueueEffectScheduler {
        int scheduled;

        @Override
        public void schedule(SchedulableEffect effect) {
            scheduled++;
            super.schedule(effect);
        }
    }
}
