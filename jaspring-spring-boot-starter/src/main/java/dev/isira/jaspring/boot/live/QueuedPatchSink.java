package dev.isira.jaspring.boot.live;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.isira.jaspring.live.PatchSet;
import dev.isira.jaspring.live.PatchSink;

/// Holds patches from turns no request waited for, until the shim polls or sends the next event.
public final class QueuedPatchSink implements PatchSink {

    private final ConcurrentLinkedQueue<PatchSet> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void push(PatchSet patches) {
        queue.add(patches);
    }

    public List<PatchSet> drain() {
        final var drained = new ArrayList<PatchSet>();
        PatchSet next;
        while ((next = queue.poll()) != null) {
            drained.add(next);
        }
        return drained;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
