package dev.isira.jaspring.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SignalOwnerTest {

    @Test
    void signalRefusesWritesFromThreadsOutsideItsOwnersTurn() throws Exception {
        final var turnThread = Thread.currentThread();
        final SignalOwner owner = new SignalOwner() {
            @Override
            public boolean mayWriteFromCurrentThread() {
                return Thread.currentThread() == turnThread;
            }

            @Override
            public String describe() {
                return "test page";
            }
        };
        final var counter = SignalOwner.runWith(owner, () -> Signals.signal(0));

        counter.set(1);
        assertEquals(1, counter.get());

        final var failure = new Throwable[1];
        final var worker = Thread.ofVirtual().start(() -> {
            try {
                counter.set(2);
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        worker.join();

        assertEquals(SignalWriteOutsideTurnException.class, failure[0].getClass());
        assertEquals(1, counter.get());
    }

    @Test
    void unownedSignalsAreUnguarded() throws Exception {
        final var counter = Signals.signal(0);
        final var worker = Thread.ofVirtual().start(() -> counter.set(5));
        worker.join();
        assertEquals(5, counter.get());
    }

    @Test
    void writesInsideComputedAreRejectedBeforeOwnershipIsChecked() {
        final var counter = Signals.signal(0);
        final var illegal = Signals.computed(() -> {
            counter.set(1);
            return 0;
        });
        assertThrows(InvalidSignalWriteException.class, illegal::get);
    }
}
