package dev.isira.jaspring.cd;

import dev.isira.jaspring.view.ComponentView;

/// Angular's `ChangeDetectorRef` for one component view. Obtain it in the constructor with `ChangeDetectorRef.current()`.
public final class ChangeDetectorRef {

    private final ComponentView view;

    ChangeDetectorRef(ComponentView view) {
        this.view = view;
    }

    /// The ref of the component being constructed or whose hook is running on this thread.
    public static ChangeDetectorRef current() {
        final var view = ComponentView.current();
        if (view == null) {
            throw new IllegalStateException("ChangeDetectorRef.current() is only available while a component is constructed or its hooks run");
        }
        return new ChangeDetectorRef(view);
    }

    public static ChangeDetectorRef of(ComponentView view) {
        return new ChangeDetectorRef(view);
    }

    /// Marks this view and its ancestors to be checked on the next tick, even if they are OnPush.
    public void markForCheck() {
        for (var current = view; current != null; current = current.parent()) {
            current.markDirty();
        }
    }

    /// Re-renders this view and its children now. Only meaningful inside a turn.
    public void detectChanges() {
        view.markDirty();
        final var detector = view.changeDetector();
        if (detector != null) detector.detectChangesIn(view);
    }

    /// Excludes this view from change detection until `reattach`; its last HTML stays on the page.
    public void detach() {
        view.setAttached(false);
    }

    public void reattach() {
        view.setAttached(true);
        view.markDirty();
    }
}
