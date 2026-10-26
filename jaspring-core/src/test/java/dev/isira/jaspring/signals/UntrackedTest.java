package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.effect;
import static dev.isira.jaspring.signals.Signals.signal;
import static dev.isira.jaspring.signals.Signals.untracked;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class UntrackedTest {

    @Test
    void readsTheLatestValue() {
        final var counter = signal(0);
        assertEquals(0, untracked(counter::get));
        counter.set(1);
        assertEquals(1, untracked(counter::get));
    }

    @Test
    void doesNotAddDependenciesToComputed() {
        final var counter = signal(0);
        final var twice = computed(() -> untracked(counter::get) * 2);
        assertEquals(0, twice.get());
        counter.set(2);
        assertEquals(0, twice.get());
    }

    @Test
    void refreshesStaleComputedWhenReadNonReactively() {
        final var counter = signal(0);
        final var twice = computed(() -> counter.get() * 2);
        assertEquals(0, untracked(twice::get));
        counter.set(2);
        assertEquals(4, untracked(twice::get));
    }

    @Test
    void doesNotMakeSurroundingEffectDependOnTheSignal() {
        final var scheduler = new QueueEffectScheduler();
        final var s = signal(1);
        final var log = new ArrayList<Integer>();
        effect(() -> log.add(untracked(s::get)), scheduler);

        scheduler.flush();
        assertEquals(List.of(1), log);
        s.set(2);
        scheduler.flush();
        assertEquals(List.of(1), log);
    }

    @Test
    void effectReactsToComputedChanges() {
        final var scheduler = new QueueEffectScheduler();
        final var count = signal(0);
        final var twice = computed(() -> count.get() * 2);
        final var log = new ArrayList<Integer>();
        effect(() -> log.add(twice.get()), scheduler);

        scheduler.flush();
        assertEquals(List.of(0), log);
        count.set(1);
        scheduler.flush();
        assertEquals(List.of(0, 2), log);
    }

    @Test
    void allSignalsInsideUntrackedAreNonReactive() {
        final var scheduler = new QueueEffectScheduler();
        final var first = signal("John");
        final var last = signal("Doe");
        final var log = new ArrayList<String>();
        effect(() -> untracked(() -> log.add(first.get() + " " + last.get())), scheduler);

        scheduler.flush();
        first.set("Patricia");
        scheduler.flush();
        last.set("Garcia");
        scheduler.flush();
        assertEquals(List.of("John Doe"), log);
    }
}
