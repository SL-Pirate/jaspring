package dev.isira.jaspring.view;

import dev.isira.jaspring.signals.internal.ReactiveNode;

/// The reactive consumer of a component view: signals read while its template renders link to it, and a write
/// to any of them marks the view dirty for the next tick.
public final class ViewConsumerNode extends ReactiveNode {

    private final ComponentView view;

    ViewConsumerNode(ComponentView view) {
        super(true, true, false);
        this.view = view;
    }

    @Override
    protected void consumerMarkedDirty() {
        view.markDirty();
    }
}
