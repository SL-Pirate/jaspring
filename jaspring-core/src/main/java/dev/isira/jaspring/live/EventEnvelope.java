package dev.isira.jaspring.live;

/// One event from the shim: the handler reference (`viewId.seq`) and the payload, either a `DomEvent`
/// or the scalar value of an `ngModelChange`.
public record EventEnvelope(String handlerRef, Object payload) {

    public long viewId() {
        return Long.parseLong(handlerRef.substring(0, handlerRef.indexOf('.')));
    }

    public int handlerSeq() {
        return Integer.parseInt(handlerRef.substring(handlerRef.indexOf('.') + 1));
    }
}
