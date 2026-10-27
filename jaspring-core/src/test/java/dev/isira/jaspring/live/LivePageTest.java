package dev.isira.jaspring.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.cd.ChangeDetectorRef;
import dev.isira.jaspring.cd.lifecycle.OnDestroy;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.component.ChangeDetectionStrategy;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.ComponentFactory;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.component.Input;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.SignalWriteOutsideTurnException;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.template.compile.TemplateCompiler;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;
import dev.isira.jaspring.zone.Zone;

class LivePageTest {

    static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();
    static final DomSchema SCHEMA = DomSchema.load(RUNTIME);
    static final TemplateCompiler COMPILER = new TemplateCompiler(new GraalAngularTemplateParser(RUNTIME), SCHEMA);

    static ComponentRegistry registry(Class<?>... components) {
        final var registry = new ComponentRegistry(ComponentFactory.reflective());
        for (final var component : components) {
            registry.registerComponent(component);
        }
        registry.compileAll(COMPILER);
        return registry;
    }

    static LivePage page(ComponentRegistry registry, Class<?> root, Zone.Mode mode, PatchSink sink) {
        return new LivePage("p1", "s1", PageSpec.of(root), registry, SCHEMA, mode, true, sink);
    }

    @Component(selector = "app-counter", template = "<button (click)=\"count.set(count() + 1)\">+</button><span>{{ count() }}</span><input [(ngModel)]=\"name\"><i>{{ name }}</i>")
    public static class Counter {
        public WritableSignal<Integer> count = Signals.signal(0);
        public String name = "";
    }

    @Test
    void eventsRunHandlersAndReturnPatchesForChangedViews() {
        final var page = page(registry(Counter.class), Counter.class, Zone.Mode.ZONE, PatchSink.NONE);
        final var html = page.renderInitial(Duration.ofSeconds(1));
        assertEquals("<app-counter data-jsp-v=\"1\"><button data-jsp-on=\"click:1.1\">+</button><span>0</span><input value=\"\" data-jsp-model=\"1.2\"><i></i></app-counter>", html);

        final var patches = page.handleEvent(new EventEnvelope("1.1", Map.of("type", "click")));
        assertEquals(1, patches.patches().size());
        assertTrue(patches.patches().get(0).html().contains("<span>1</span>"));

        final var model = page.handleEvent(new EventEnvelope("1.2", "Ann"));
        assertTrue(model.patches().get(0).html().contains("<i>Ann</i>"));
        assertTrue(model.patches().get(0).html().contains("value=\"Ann\""));

        assertTrue(page.handleEvent(new EventEnvelope("1.1", Map.of("type", "click"))).patches().get(0).html().contains("<span>2</span>"));
    }

    @Component(selector = "app-child", template = "{{ label }}:{{ renders() }}", changeDetection = ChangeDetectionStrategy.ON_PUSH)
    public static class OnPushChild {
        @Input public String label;
        public int renderCount;
        public int renders() { return ++renderCount; }
    }

    @Component(selector = "app-plain", template = "{{ renders() }}")
    public static class DefaultChild {
        public int renderCount;
        public int renders() { return ++renderCount; }
    }

    @Component(selector = "app-parent", template = "<app-child [label]=\"label\"></app-child><app-plain></app-plain><button (click)=\"noop()\">x</button><button (click)=\"label = 'B'\">y</button>")
    public static class Parent {
        public String label = "A";
        public void noop() {}
    }

    @Test
    void zoneModeRefreshesDefaultViewsAndSkipsCleanOnPushViews() {
        final var page = page(registry(OnPushChild.class, DefaultChild.class, Parent.class), Parent.class, Zone.Mode.ZONE, PatchSink.NONE);
        final var html = page.renderInitial(Duration.ofSeconds(1));
        assertTrue(html.contains("A:1"));

        var patches = page.handleEvent(new EventEnvelope("1.1", Map.of("type", "click")));
        assertEquals(1, patches.patches().size());
        assertEquals(3, patches.patches().get(0).viewId(), "only the Default child re-rendered");
        assertTrue(patches.patches().get(0).html().contains(">2<"));

        patches = page.handleEvent(new EventEnvelope("1.2", Map.of("type", "click")));
        final var ids = patches.patches().stream().map(Patch::viewId).sorted().toList();
        assertEquals(List.of(2L, 3L), ids, "input change re-renders the OnPush child");
        assertTrue(patches.patches().get(0).html().contains("B:2"));
    }

    @Component(selector = "app-leaf", template = "{{ value() }}")
    public static class Leaf {
        public static WritableSignal<Integer> shared;
        public WritableSignal<Integer> value = Signals.signal(1);
        public Leaf() { shared = value; }
    }

    @Component(selector = "app-root-z", template = "<p>{{ renders() }}</p><app-leaf></app-leaf>")
    public static class ZonelessRoot {
        public int renderCount;
        public int renders() { return ++renderCount; }
    }

    @Test
    void zonelessModeReRendersOnlyTheViewThatReadTheChangedSignal() {
        final var pushed = new CopyOnWriteArrayList<PatchSet>();
        final var page = page(registry(Leaf.class, ZonelessRoot.class), ZonelessRoot.class, Zone.Mode.ZONELESS, pushed::add);
        page.renderInitial(Duration.ofSeconds(1));

        page.runTurn(() -> {
            Leaf.shared.set(5);
            return null;
        });

        assertEquals(1, pushed.size());
        assertEquals(1, pushed.get(0).patches().size());
        assertEquals(2L, pushed.get(0).patches().get(0).viewId());
        assertEquals("<app-leaf data-jsp-v=\"2\">5</app-leaf>", pushed.get(0).patches().get(0).html());
        assertEquals(1, ((ZonelessRoot) page.tree().root().instance()).renderCount);
    }

    @Component(selector = "app-async", template = "@if (users() === null) {loading} @else {<ul>@for (u of users(); track u) {<li>{{ u }}</li>}</ul>}")
    public static class AsyncList implements OnInit {
        public WritableSignal<List<String>> users = Signals.signal(null);
        static CountDownLatch gate = new CountDownLatch(0);

        @Override
        public void ngOnInit() {
            Zone.current().fork(() -> {
                gate.await(2, TimeUnit.SECONDS);
                return List.of("ann", "bob");
            }).thenAccept(users::set);
        }
    }

    @Test
    void initialRenderWaitsForForkedWorkInsideTheZone() {
        AsyncList.gate = new CountDownLatch(0);
        final var pushed = new CopyOnWriteArrayList<PatchSet>();
        final var page = page(registry(AsyncList.class), AsyncList.class, Zone.Mode.ZONE, pushed::add);
        final var html = page.renderInitial(Duration.ofSeconds(2));

        assertTrue(html.contains("<li>ann</li><li>bob</li>"), html);
        assertTrue(pushed.isEmpty(), "settled before the response, nothing to push");
        assertTrue(page.zone().isStable());
    }

    @Test
    void lateForkCompletionsArePushedAsPatches() throws Exception {
        AsyncList.gate = new CountDownLatch(1);
        final var pushed = new CopyOnWriteArrayList<PatchSet>();
        final var arrived = new CountDownLatch(1);
        final var settled = new CountDownLatch(1);
        final var page = page(registry(AsyncList.class), AsyncList.class, Zone.Mode.ZONE, patches -> {
            pushed.add(patches);
            arrived.countDown();
        });
        page.zone().onStable(settled::countDown);
        final var html = page.renderInitial(Duration.ofMillis(200));

        assertTrue(html.contains("loading"), html);
        AsyncList.gate.countDown();
        assertTrue(arrived.await(2, TimeUnit.SECONDS));
        assertTrue(pushed.get(0).patches().get(0).html().contains("<li>ann</li>"));
        // Patches are delivered before the completion turn marks the zone stable.
        assertTrue(settled.await(2, TimeUnit.SECONDS), "page should settle after publishing its patches");
        assertTrue(page.zone().isStable());
    }

    @Test
    void signalsOfAPageRefuseWritesFromForeignThreads() throws Exception {
        final var page = page(registry(Counter.class), Counter.class, Zone.Mode.ZONE, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));
        final var counter = (Counter) page.tree().root().instance();
        final var created = page.runTurn(() -> Signals.signal(0));

        final var failure = new Throwable[1];
        final var worker = Thread.ofVirtual().start(() -> {
            try {
                created.set(1);
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        worker.join();
        assertEquals(SignalWriteOutsideTurnException.class, failure[0].getClass());
        assertEquals(0, counter.count.get());
    }

    @Test
    void eventsOnOnePageAreSerialized() throws Exception {
        final var page = page(registry(Counter.class), Counter.class, Zone.Mode.ZONE, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));
        final var threads = new ArrayList<Thread>();
        for (var i = 0; i < 50; i++) {
            threads.add(Thread.ofVirtual().start(() -> page.handleEvent(new EventEnvelope("1.1", Map.of("type", "click")))));
        }
        for (final var thread : threads) {
            thread.join();
        }
        assertEquals(50, ((Counter) page.tree().root().instance()).count.get());
    }

    @Component(selector = "app-cd", template = "{{ value }}", changeDetection = ChangeDetectionStrategy.ON_PUSH)
    public static class ManualCheck implements OnInit, OnDestroy {
        public String value = "a";
        public ChangeDetectorRef cd;
        public static boolean destroyed;

        @Override
        public void ngOnInit() {
            cd = ChangeDetectorRef.current();
        }

        @Override
        public void ngOnDestroy() {
            destroyed = true;
        }
    }

    @Test
    void markForCheckAndDestroy() {
        ManualCheck.destroyed = false;
        final var pushed = new CopyOnWriteArrayList<PatchSet>();
        final var page = page(registry(ManualCheck.class), ManualCheck.class, Zone.Mode.ZONE, pushed::add);
        page.renderInitial(Duration.ofSeconds(1));
        final var component = (ManualCheck) page.tree().root().instance();

        page.runTurn(() -> {
            component.value = "b";
            return null;
        });
        assertTrue(pushed.isEmpty(), "OnPush root without markForCheck stays as is");

        page.runTurn(() -> {
            component.cd.markForCheck();
            return null;
        });
        assertEquals("<app-cd data-jsp-v=\"1\">b</app-cd>", pushed.get(0).patches().get(0).html());

        page.destroy();
        assertTrue(ManualCheck.destroyed);
        assertThrows(PageGoneException.class, () -> page.handleEvent(new EventEnvelope("1.1", Map.of())));
    }

    @Test
    void registryBindsPagesToSessionsAndEvictsThem() {
        final var registry = registry(Counter.class);
        final var now = Instant.parse("2026-01-01T00:00:00Z");
        final var clock = new Clock[] {Clock.fixed(now, ZoneOffset.UTC)};
        final var pages = new LivePageRegistry(Duration.ofSeconds(60), 2, new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return clock[0].instant(); }
        });
        final var a = new LivePage("a", "s1", PageSpec.of(Counter.class), registry, SCHEMA, Zone.Mode.ZONE, true, PatchSink.NONE);
        final var b = new LivePage("b", "s1", PageSpec.of(Counter.class), registry, SCHEMA, Zone.Mode.ZONE, true, PatchSink.NONE);
        final var c = new LivePage("c", "s1", PageSpec.of(Counter.class), registry, SCHEMA, Zone.Mode.ZONE, true, PatchSink.NONE);
        a.renderInitial(Duration.ofSeconds(1));
        pages.register(a);
        pages.register(b);

        assertTrue(pages.find("a", "s1").isPresent());
        assertFalse(pages.find("a", "other-session").isPresent());
        assertFalse(pages.find("nope", "s1").isPresent());

        pages.register(c);
        assertEquals(2, pages.size(), "third page in the session evicts the least recently used");
        assertTrue(a.isDestroyed() || b.isDestroyed());

        pages.connected("c");
        clock[0] = Clock.fixed(now.plusSeconds(120), ZoneOffset.UTC);
        assertEquals(1, pages.evictExpired(), "the disconnected page expired, the connected one stays");
        assertEquals(1, pages.size());
        assertTrue(pages.find("c", "s1").isPresent());
    }
}
