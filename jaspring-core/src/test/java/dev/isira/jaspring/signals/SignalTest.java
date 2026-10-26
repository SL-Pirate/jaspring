package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SignalTest {

    @Test
    void getterReflectsTheSetValue() {
        final var state = signal(false);
        assertFalse(state.get());
        state.set(true);
        assertTrue(state.get());
    }

    @Test
    void updateDerivesTheNewValueFromThePreviousOne() {
        final var counter = signal(0);
        counter.update(c -> c + 1);
        assertEquals(1, counter.get());
    }

    @Test
    void equalValuesDoNotUpdateTheSignal() {
        final var state = signal("aaa", (a, b) -> a.length() == b.length());

        state.set("bbb");
        assertEquals("aaa", state.get());
        state.update(s -> "ccc");
        assertEquals("aaa", state.get());
        state.set("d");
        assertEquals("d", state.get());
    }

    @Test
    void equalValuesDoNotPropagateToComputeds() {
        final var state = signal("aaa", (a, b) -> a.length() == b.length());
        final var upper = computed(() -> state.get().toUpperCase());

        state.set("bbb");
        assertEquals("AAA", upper.get());
        state.set("d");
        assertEquals("D", upper.get());
    }

    @Test
    void defaultEqualityUsesEqualsNotIdentity() {
        final var state = signal(new Object());
        final var computeCount = new int[1];
        final var derived = computed(() -> state.get().getClass().getSimpleName() + ":" + ++computeCount[0]);
        assertEquals("Object:1", derived.get());

        state.set(state.get());
        assertEquals("Object:1", derived.get());

        state.set(new Object());
        assertEquals("Object:2", derived.get());

        // Records are value types: an equal record is not a change.
        final var point = signal(new Point(1, 2));
        final var reads = new int[1];
        final var seen = computed(() -> point.get().x() + ":" + ++reads[0]);
        assertEquals("1:1", seen.get());
        point.set(new Point(1, 2));
        assertEquals("1:1", seen.get());
    }

    @Test
    void customEqualityRunsEvenForTheSameReference() {
        final var state = new Object();
        final var stateSignal = signal(state, (a, b) -> false);
        final var computeCount = new int[1];
        final var derived = computed(() -> stateSignal.get().hashCode() + ":" + ++computeCount[0]);

        assertEquals(state.hashCode() + ":1", derived.get());
        stateSignal.set(state);
        assertEquals(state.hashCode() + ":2", derived.get());
        stateSignal.update(s -> s);
        assertEquals(state.hashCode() + ":3", derived.get());
    }

    @Test
    void readonlyViewTracksTheWritableSignal() {
        final var counter = signal(0);
        final var readOnly = counter.asReadonly();
        final var twice = computed(() -> readOnly.get() * 2);

        assertEquals(0, twice.get());
        counter.set(2);
        assertEquals(4, twice.get());
    }

    @Test
    void hasAReadableToString() {
        assertEquals("[Signal: false]", signal(false).toString());
    }

    record Point(int x, int y) {}
}
