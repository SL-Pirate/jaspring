package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static dev.isira.jaspring.signals.Signals.effect;
import static dev.isira.jaspring.signals.Signals.signal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EffectTest {

    private final QueueEffectScheduler scheduler = new QueueEffectScheduler();

    @Test
    void runsOnceEvenWithoutDependencies() {
        final var runs = new int[1];
        effect(() -> runs[0]++, scheduler);
        scheduler.flush();
        scheduler.flush();
        assertEquals(1, runs[0]);
    }

    @Test
    void schedulesOnSignalChange() {
        final var count = signal(0);
        final var log = new ArrayList<Integer>();
        effect(() -> log.add(count.get()), scheduler);

        scheduler.flush();
        assertEquals(List.of(0), log);
        count.set(1);
        scheduler.flush();
        assertEquals(List.of(0, 1), log);
    }

    @Test
    void doesNotScheduleWhenAPreviousDependencyChanges() {
        final var countA = signal(0);
        final var countB = signal(100);
        final var useA = signal(true);
        final var log = new ArrayList<Integer>();
        effect(() -> log.add(useA.get() ? countA.get() : countB.get()), scheduler);

        scheduler.flush();
        assertEquals(List.of(0), log);
        countB.update(v -> v + 1);
        scheduler.flush();
        assertEquals(List.of(0), log);
        countA.update(v -> v + 1);
        scheduler.flush();
        assertEquals(List.of(0, 1), log);
        useA.set(false);
        scheduler.flush();
        assertEquals(List.of(0, 1, 101), log);
        countA.update(v -> v + 1);
        scheduler.flush();
        assertEquals(List.of(0, 1, 101), log);
    }

    @Test
    void doesNotRunWhenIntermediateComputedIsUnchanged() {
        final var source = signal(0);
        final var isEven = computed(() -> source.get() % 2 == 0);
        final var runs = new int[1];
        effect(() -> {
            isEven.get();
            runs[0]++;
        }, scheduler);

        scheduler.flush();
        assertEquals(1, runs[0]);
        source.set(1);
        scheduler.flush();
        assertEquals(2, runs[0]);
        source.set(3);
        scheduler.flush();
        assertEquals(2, runs[0]);
        source.set(4);
        scheduler.flush();
        assertEquals(3, runs[0]);
    }

    @Test
    void cleanupRunsBeforeTheNextRun() {
        final var source = signal(0);
        final var seen = new ArrayList<Integer>();
        effect(cleanup -> {
            seen.add(source.get());
            cleanup.onCleanup(() -> {
                if (seen.size() == 2) seen.clear();
            });
        }, scheduler);

        scheduler.flush();
        assertEquals(List.of(0), seen);
        source.update(c -> c + 1);
        scheduler.flush();
        assertEquals(List.of(0, 1), seen);
        source.update(c -> c + 1);
        scheduler.flush();
        assertEquals(List.of(2), seen);
    }

    @Test
    void cleanupIsForgottenAfterItRan() {
        final var source = signal(0);
        final var seen = new ArrayList<Integer>();
        effect(cleanup -> {
            final var value = source.get();
            seen.add(value);
            if (value == 0) cleanup.onCleanup(seen::clear);
        }, scheduler);

        scheduler.flush();
        source.set(2);
        scheduler.flush();
        assertEquals(List.of(2), seen);
        source.set(3);
        scheduler.flush();
        assertEquals(List.of(2, 3), seen);
    }

    @Test
    void cleanupsRunInReverseOrder() {
        final var source = signal(0);
        final var log = new ArrayList<String>();
        effect(cleanup -> {
            source.get();
            cleanup.onCleanup(() -> log.add("first"));
            cleanup.onCleanup(() -> log.add("second"));
        }, scheduler);

        scheduler.flush();
        source.set(1);
        scheduler.flush();
        assertEquals(List.of("second", "first"), log);
    }

    @Test
    void readingASignalDuringNotificationThrows() {
        final var source = signal(0);
        final var armed = new boolean[1];
        final var ran = new boolean[1];
        final var probing = new QueueEffectScheduler() {
            @Override
            public void schedule(SchedulableEffect effect) {
                if (armed[0]) {
                    ran[0] = true;
                    assertThrows(IllegalStateException.class, source::get);
                }
                super.schedule(effect);
            }
        };
        effect(() -> source.get(), probing);
        probing.flush();
        armed[0] = true;

        source.set(1);
        assertTrue(ran[0]);
    }

    @Test
    void destroyedEffectsDoNotRunOrSchedule() {
        final var counter = signal(0);
        final var runs = new int[1];
        final var ref = effect(() -> {
            counter.get();
            runs[0]++;
        }, scheduler);
        scheduler.flush();
        assertEquals(1, runs[0]);

        ref.destroy();
        counter.set(1);
        scheduler.flush();
        assertEquals(1, runs[0]);
    }

    @Test
    void destroyRunsCleanupOnce() {
        final var counter = signal(0);
        final var cleanups = new int[1];
        final var ref = effect(cleanup -> {
            counter.get();
            cleanup.onCleanup(() -> cleanups[0]++);
        }, scheduler);
        scheduler.flush();

        ref.destroy();
        assertEquals(1, cleanups[0]);
        ref.destroy();
        assertEquals(1, cleanups[0]);
    }

    @Test
    void effectsMayWriteSignals() {
        final var source = signal(1);
        final var mirror = signal(0);
        effect(() -> mirror.set(source.get() * 10), scheduler);

        scheduler.flush();
        assertEquals(10, mirror.get());
        source.set(2);
        scheduler.flush();
        assertEquals(20, mirror.get());
    }

    @Test
    void effectNeedsASchedulerOrAContext() {
        assertThrows(IllegalStateException.class, () -> effect(() -> {}));

        final var runs = new int[1];
        EffectContext.runWith(scheduler, () -> effect(() -> runs[0]++));
        scheduler.flush();
        assertEquals(1, runs[0]);
    }
}
