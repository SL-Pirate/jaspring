package dev.isira.jaspring.cd;

import dev.isira.jaspring.component.ChangeDetectionStrategy;
import dev.isira.jaspring.signals.QueueEffectScheduler;
import dev.isira.jaspring.view.ComponentView;
import dev.isira.jaspring.view.ViewRenderer;
import dev.isira.jaspring.view.ViewTree;
import dev.isira.jaspring.zone.Zone;

/// Angular's `ApplicationRef.tick` for a page: in zone mode every Default view is re-rendered on each tick and
/// OnPush views only when dirty; in zoneless mode only dirty views are re-rendered. Effects run before the views,
/// and the tick repeats while effects or renders dirty something else, up to Angular's limit of ten passes.
public final class ChangeDetector {

    private static final int MAX_PASSES = 10;

    private final Zone.Mode mode;
    private final ViewTree tree;
    private final ViewRenderer renderer;
    private final QueueEffectScheduler effects;

    public ChangeDetector(Zone.Mode mode, ViewTree tree, ViewRenderer renderer, QueueEffectScheduler effects) {
        this.mode = mode;
        this.tree = tree;
        this.renderer = renderer;
        this.effects = effects;
    }

    public void tick() {
        final var root = tree.root();
        if (root == null) return;
        for (var pass = 0; pass < MAX_PASSES; pass++) {
            effects.flush();
            final var global = mode == Zone.Mode.ZONE;
            if (!global && !hasDirty(root)) return;
            check(root, global);
            if (!effects.hasDirtyEffects() && !hasDirty(root)) return;
        }
        throw new IllegalStateException("Infinite change detection: still dirty after " + MAX_PASSES + " passes");
    }

    /// After creation every view is freshly rendered; only effects and what they dirtied need another pass.
    public void settle() {
        final var root = tree.root();
        if (root == null) return;
        for (var pass = 0; pass < MAX_PASSES; pass++) {
            effects.flush();
            if (!hasDirty(root)) return;
            check(root, false);
            if (!effects.hasDirtyEffects() && !hasDirty(root)) return;
        }
        throw new IllegalStateException("Infinite change detection: still dirty after " + MAX_PASSES + " passes");
    }

    /// Angular's `detectChangesInView`: refresh when the mode says so or the view is dirty, otherwise descend
    /// to dirty descendants. A refreshed view re-renders its children through its template.
    public void check(ComponentView view, boolean global) {
        if (view.isDestroyed() || !view.isAttached()) return;
        final var refresh = (global && view.def().changeDetection() == ChangeDetectionStrategy.DEFAULT) || view.isDirty();
        if (refresh) {
            renderer.refresh(tree, view, this);
            return;
        }
        for (final var child : java.util.List.copyOf(view.children().values())) {
            check(child, false);
        }
    }

    /// `ChangeDetectorRef.detectChanges()`.
    public void detectChangesIn(ComponentView view) {
        check(view, true);
    }

    public boolean isGlobal() {
        return mode == Zone.Mode.ZONE;
    }

    private static boolean hasDirty(ComponentView view) {
        if (view.isDirty() && view.isAttached()) return true;
        for (final var child : view.children().values()) {
            if (hasDirty(child)) return true;
        }
        return false;
    }
}
