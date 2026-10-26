package dev.isira.jaspring.live;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import dev.isira.jaspring.cd.ChangeDetector;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.di.PageScope;
import dev.isira.jaspring.signals.EffectContext;
import dev.isira.jaspring.signals.QueueEffectScheduler;
import dev.isira.jaspring.signals.SignalOwner;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.template.ast.EventType;
import dev.isira.jaspring.template.eval.EvaluationContext;
import dev.isira.jaspring.template.eval.ExpressionEvaluator;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.view.ComponentView;
import dev.isira.jaspring.view.ViewRenderer;
import dev.isira.jaspring.view.ViewTree;
import dev.isira.jaspring.zone.TurnExecutor;
import dev.isira.jaspring.zone.Zone;

/// One rendered page kept alive between requests: its component tree, zone, effects and page-scoped beans.
///
/// Every piece of work on the page is a turn: it takes the page lock, binds the zone, the signal owner, the
/// effect scheduler and the page scope, runs the body, runs change detection and collects patches. Turns of one
/// page are serialized; different pages run concurrently on virtual threads.
public final class LivePage implements TurnExecutor, SignalOwner {

    private final String id;
    private final String sessionId;
    private final ReentrantLock lock = new ReentrantLock();
    private final Zone zone;
    private final ViewTree tree = new ViewTree();
    private final ViewRenderer renderer;
    private final QueueEffectScheduler effects = new QueueEffectScheduler();
    private final ChangeDetector detector;
    private final PageScope.Beans beans;
    private final PageSpec spec;
    private volatile PatchSink sink;
    private volatile Instant lastActivity = Instant.now();
    private volatile boolean destroyed;
    private boolean requestTurn;
    private int turnDepth;
    private String redirect;

    public LivePage(String id, String sessionId, PageSpec spec, ComponentRegistry registry, DomSchema schema, Zone.Mode mode,
            boolean liveEvents, PatchSink sink) {
        this.id = id;
        this.sessionId = sessionId;
        this.spec = spec;
        this.sink = sink;
        this.zone = new Zone(mode, this, lock);
        this.renderer = new ViewRenderer(registry, schema, liveEvents);
        this.detector = new ChangeDetector(mode, tree, renderer, effects);
        this.beans = new PageScope.Beans(id);
    }

    public String id() {
        return id;
    }

    public String sessionId() {
        return sessionId;
    }

    public Zone zone() {
        return zone;
    }

    public ViewTree tree() {
        return tree;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void sink(PatchSink sink) {
        this.sink = sink;
    }

    // ---- SignalOwner ----

    @Override
    public boolean mayWriteFromCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    @Override
    public String describe() {
        return "page " + id;
    }

    // ---- turns ----

    /// Runs `body` as a turn. Turns started while a request holds the page produce that request's patches;
    /// turns started by forked work push theirs to the sink.
    @Override
    public <T> T runTurn(Supplier<T> body) {
        return runTurn(body, true);
    }

    private <T> T runTurn(Supplier<T> body, boolean fullTick) {
        lock.lock();
        try {
            if (destroyed) throw new PageGoneException(id);
            lastActivity = Instant.now();
            turnDepth++;
            final T result;
            try {
                result = bind(() -> zone.run(body));
            } finally {
                turnDepth--;
            }
            if (turnDepth == 0) {
                bind(() -> {
                    if (fullTick) detector.tick(); else detector.settle();
                    return null;
                });
                if (!requestTurn) {
                    final var patches = collectPatches();
                    if (!patches.isEmpty()) sink.push(patches);
                }
                zone.checkStable();
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private <T> T bind(Supplier<T> body) {
        return SignalOwner.runWith(this, () -> EffectContext.runWith(effects, () -> PageScope.runWith(beans, body)));
    }

    /// Renders the page for the first time and waits until forked work settles, or the timeout passes.
    public String renderInitial(Duration stabilityTimeout) {
        lock.lock();
        try {
            requestTurn = true;
            // Creation already rendered every view; only effects scheduled during construction need settling.
            runTurn(() -> renderer.createRoot(tree, spec.rootComponent(), spec.inputs(), detector), false);
            clearChangeFlags();
            if (!zone.whenStable(stabilityTimeout)) {
                // Late completions will arrive as pushed patches instead.
            }
            clearChangeFlags();
            return tree.assemble(tree.root());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the page to settle", e);
        } finally {
            requestTurn = false;
            lock.unlock();
        }
    }

    /// Runs the handler the shim pointed at, then change detection, and returns the changed views.
    public PatchSet handleEvent(EventEnvelope envelope) {
        lock.lock();
        try {
            requestTurn = true;
            runTurn(() -> {
                dispatch(envelope);
                return null;
            });
            return collectPatches();
        } finally {
            requestTurn = false;
            lock.unlock();
        }
    }

    /// The whole page again, for a shim that reconnected.
    public PatchSet renderFull() {
        lock.lock();
        try {
            clearChangeFlags();
            return new PatchSet(List.of(new Patch(tree.root().id(), tree.assemble(tree.root()))), null);
        } finally {
            lock.unlock();
        }
    }

    public void redirect(String url) {
        redirect = url;
    }

    private void dispatch(EventEnvelope envelope) {
        final var view = tree.view(envelope.viewId());
        if (view == null || view.isDestroyed()) {
            throw new IllegalArgumentException("Unknown view " + envelope.viewId());
        }
        final var binding = view.handler(envelope.handlerSeq());
        if (binding == null) {
            throw new IllegalArgumentException("Unknown handler " + envelope.handlerRef());
        }
        // An event inside a view marks it and its ancestors dirty, so OnPush views re-render after their own events.
        for (var current = view; current != null; current = current.parent()) {
            current.markDirty();
        }
        final var payload = envelope.payload() instanceof java.util.Map<?, ?> map && !(envelope.payload() instanceof DomEvent)
                ? new DomEvent(castMap(map))
                : envelope.payload();
        final var scope = binding.scope().with("$event", payload);
        final var context = EvaluationContext.of(view.instance()).withPipes(renderer.registry()).withScope(scope);
        Signals.untracked(() -> {
            if (binding.event().type() == EventType.TWO_WAY) {
                ExpressionEvaluator.assignTo(binding.event().handler(), payload, context);
            } else {
                ExpressionEvaluator.evaluate(binding.event().handler(), context);
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> castMap(java.util.Map<?, ?> map) {
        return (java.util.Map<String, Object>) map;
    }

    /// Topmost views whose HTML changed since the last collection; a patched ancestor covers its descendants.
    private PatchSet collectPatches() {
        final var patches = new ArrayList<Patch>();
        collect(tree.root(), patches);
        clearChangeFlags();
        final var set = new PatchSet(List.copyOf(patches), redirect);
        redirect = null;
        return set;
    }

    private void collect(ComponentView view, List<Patch> patches) {
        if (view == null || view.isDestroyed()) return;
        if (view.htmlChanged() || view.hostChanged()) {
            patches.add(new Patch(view.id(), tree.assemble(view)));
            return;
        }
        for (final var child : view.children().values()) {
            collect(child, patches);
        }
    }

    private void clearChangeFlags() {
        for (final var view : tree.views()) {
            view.clearHtmlChanged();
            view.clearHostChanged();
        }
    }

    public void destroy() {
        lock.lock();
        try {
            if (destroyed) return;
            destroyed = true;
            bind(() -> {
                if (tree.root() != null) renderer.destroy(tree, tree.root());
                beans.destroy();
                return null;
            });
        } finally {
            lock.unlock();
        }
    }
}
