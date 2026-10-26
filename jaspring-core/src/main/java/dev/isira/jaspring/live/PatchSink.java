package dev.isira.jaspring.live;

/// Receives patches produced by turns that no request is waiting for (forked work finishing later).
/// The WebSocket transport pushes them; the HTTP transport queues them for the next poll.
@FunctionalInterface
public interface PatchSink {

    PatchSink NONE = patches -> { };

    void push(PatchSet patches);
}
