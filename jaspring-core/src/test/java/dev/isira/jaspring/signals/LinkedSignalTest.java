package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.effect;
import static dev.isira.jaspring.signals.Signals.linkedSignal;
import static dev.isira.jaspring.signals.Signals.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LinkedSignalTest {

    @Test
    void conformsToTheWritableSignalContract() {
        final var firstLetter = linkedSignal(signal("foo")::get, (str, previous) -> str.substring(0, 1));

        firstLetter.set("a");
        assertEquals("a", firstLetter.get());
        firstLetter.update(v -> "b");
        assertEquals("b", firstLetter.get());
        final var readonly = firstLetter.asReadonly();
        firstLetter.set("c");
        assertEquals("c", readonly.get());
        assertEquals("[LinkedSignal: c]", firstLetter.toString());
    }

    @Test
    void updatesWhenTheSourceChanges() {
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(options::get, (opts, previous) -> opts.get(0));

        assertEquals("apple", choice.get());
        choice.set("fig");
        assertEquals("fig", choice.get());
        options.set(List.of("orange", "apple", "pomegranate"));
        assertEquals("orange", choice.get());
    }

    @Test
    void exposesThePreviousValueToTheComputation() {
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(options::get, (opts, previous) ->
                previous != null && opts.contains(previous.value()) ? previous.value() : opts.get(0));

        assertEquals("apple", choice.get());
        options.set(List.of("orange", "apple", "pomegranate"));
        assertEquals("apple", choice.get());
    }

    @Test
    void exposesThePreviousSourceToTheComputation() {
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = Signals.<List<String>, Integer>linkedSignal(options::get, (opts, previous) -> {
            if (previous == null) return 0;
            final var prevChoice = previous.source().get(previous.value());
            final var index = opts.indexOf(prevChoice);
            return index == -1 ? 0 : index;
        });
        final var twice = computed(() -> choice.get() * 2);

        assertEquals(0, twice.get());
        choice.set(2);
        options.set(List.of("banana", "fig", "apple"));
        assertEquals(2, twice.get());
        assertEquals(1, choice.get());
    }

    @Test
    void equalityAppliesToBothSourceAndExplicitUpdates() {
        final var counter = signal(0);
        final var choice = linkedSignal(counter::get, (c, previous) -> c, (a, b) -> true);

        assertEquals(0, choice.get());
        counter.update(c -> c + 1);
        assertEquals(0, choice.get());
        choice.set(10);
        assertEquals(0, choice.get());
    }

    @Test
    void updateThrowsWhenTheComputationFailed() {
        final var source = Signals.<Integer>linkedSignal(() -> {
            throw new IllegalStateException("failure");
        });
        final var updaterRan = new boolean[1];

        final var error = assertThrows(IllegalStateException.class, () -> source.update(v -> {
            updaterRan[0] = true;
            return 0;
        }));
        assertEquals("failure", error.getMessage());
        assertFalse(updaterRan[0]);
    }

    @Test
    void equalComputedValueDoesNotRecomputeDownstream() {
        final var source = signal(0);
        final var isEven = linkedSignal(() -> source.get() % 2 == 0);
        final var updates = new int[1];
        final var tracker = computed(() -> {
            isEven.get();
            return updates[0]++;
        });

        tracker.get();
        assertEquals(1, updates[0]);
        assertTrue(isEven.get());
        isEven.set(false);
        tracker.get();
        assertEquals(2, updates[0]);
        source.set(1);
        tracker.get();
        assertEquals(2, updates[0]);
        source.set(4);
        tracker.get();
        assertEquals(3, updates[0]);
    }

    @Test
    void shorthandFormDerivesFromTheComputation() {
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(() -> options.get().get(0));

        assertEquals("apple", choice.get());
        choice.set("orange");
        assertEquals(List.of("apple", "banana", "fig"), options.get());
        assertEquals("orange", choice.get());
        options.set(List.of("banana", "fig"));
        assertEquals("banana", choice.get());
    }

    @Test
    void explicitSetWinsOverPendingSourceChanges() {
        final var counter = signal(0);
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(() -> options.get().get(0));
        assertEquals("apple", choice.get());

        options.set(List.of("orange", "apple", "pomegranate"));
        choice.set("watermelon");
        counter.update(c -> c + 1);
        assertEquals("watermelon", choice.get());

        choice.set("persimmon");
        options.set(List.of("apple", "banana", "fig"));
        assertEquals("apple", choice.get());

        options.set(List.of("orange", "apple", "pomegranate"));
        choice.update(String::toUpperCase);
        assertEquals("ORANGE", choice.get());
    }

    @Test
    void explicitSetWinsOverPendingSourceChangesForLiveConsumers() {
        final var scheduler = new QueueEffectScheduler();
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(() -> options.get().get(0));
        assertEquals("apple", choice.get());
        final var ref = effect(() -> choice.get(), scheduler);
        scheduler.flush();

        options.set(List.of("orange", "apple", "pomegranate"));
        choice.set("watermelon");
        assertEquals("watermelon", choice.get());

        choice.set("persimmon");
        options.set(List.of("apple", "banana", "fig"));
        assertEquals("apple", choice.get());

        options.set(List.of("orange", "apple", "pomegranate"));
        choice.update(String::toUpperCase);
        assertEquals("ORANGE", choice.get());
        ref.destroy();
    }

    @Test
    void capturesDependenciesEvenWhenSetBeforeTheFirstRead() {
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(() -> options.get().get(0));

        choice.set("watermelon");
        assertEquals("watermelon", choice.get());
        options.set(List.of("orange", "apple", "pomegranate"));
        assertEquals("orange", choice.get());
    }

    @Test
    void pullsTheLatestSourceBeforeAnUpdate() {
        final var options = signal(List.of("apple", "banana", "fig"));
        final var choice = linkedSignal(() -> options.get().get(0));

        choice.update(String::toUpperCase);
        assertEquals("APPLE", choice.get());
    }

    @Test
    void explicitSetClearsTheErrorState() {
        final var choice = Signals.<String>linkedSignal(() -> {
            throw new IllegalStateException("Can't compute");
        });

        assertThrows(IllegalStateException.class, choice::get);
        choice.set("explicit");
        assertEquals("explicit", choice.get());
    }

    @Test
    void recoversFromEqualityExceptionWithoutAPreviousValue() {
        final var shouldThrow = new boolean[] {true};
        final var source = signal(0);
        final var derived = linkedSignal(source::get,
                (value, previous) -> value + ", hasPrevious: " + (previous != null),
                (a, b) -> {
                    if (shouldThrow[0]) throw new IllegalStateException("equal");
                    return a.equals(b);
                });

        assertEquals("0, hasPrevious: false", derived.get());
        source.set(1);
        assertThrows(IllegalStateException.class, derived::get);
        shouldThrow[0] = false;
        source.set(2);
        assertEquals("2, hasPrevious: false", derived.get());
    }
}
