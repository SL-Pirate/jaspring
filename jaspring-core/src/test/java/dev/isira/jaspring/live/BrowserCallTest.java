package dev.isira.jaspring.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.browser.Browser;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.zone.Zone;
import dev.isira.jaspring.zone.ZoneTask;

class BrowserCallTest {

    @Component(selector = "app-measure", template = "<canvas data-jsp-hook=\"spark\" (point-picked)=\"pick($event.detail.index)\"></canvas>"
            + "<button (click)=\"measure()\">m</button><span>{{ size() }}</span><i>{{ picked }}</i>")
    public static class Measure {
        static ZoneTask<Object> last;
        public WritableSignal<String> size = Signals.signal("?");
        public Object picked;

        public void measure() {
            last = Browser.current().call("spark.size", 2);
            last.whenComplete((value, error) -> size.set(error == null ? String.valueOf(value) : "error:" + error.getMessage()));
        }

        public void pick(Object index) {
            picked = index;
        }
    }

    @Component(selector = "app-viewport", template = "<b>{{ width() }}</b>")
    public static class Viewport implements OnInit {
        public WritableSignal<String> width = Signals.signal("?");

        @Override
        public void ngOnInit() {
            Browser.current().call("viewport").whenComplete((value, error) ->
                    width.set(error == null ? String.valueOf(value) : "error:" + error.getMessage()));
        }
    }

    static LivePage page(Class<?> root, boolean liveEvents, PatchSink sink) {
        return new LivePage("p1", "s1", PageSpec.of(root), LivePageTest.registry(root), LivePageTest.SCHEMA, Zone.Mode.ZONE, liveEvents, sink);
    }

    @Test
    void callIssuedInAHandlerIsCollectedWithThePatchesAndCompletesInsideATurn() {
        final var page = page(Measure.class, true, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));

        final var reply = page.handleEvent(new EventEnvelope("1.2", Map.of("type", "click")));
        assertEquals(1, reply.calls().size());
        final var call = reply.calls().get(0);
        assertEquals("spark.size", call.target());
        assertEquals(1L, call.viewId());
        assertEquals(List.of(2), call.args());
        assertFalse(page.zone().isStable(), "unstable while the browser has not answered");

        final var patches = page.handleResult(new BrowserResult(call.id(), 320, null));
        assertTrue(patches.patches().get(0).html().contains("<span>320</span>"), patches.toString());
        assertTrue(patches.calls().isEmpty());
        assertTrue(page.zone().isStable());
        assertTrue(Measure.last.isDone());
    }

    @Test
    void clientErrorsFailTheTaskInsideATurn() {
        final var page = page(Measure.class, true, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));
        final var call = page.handleEvent(new EventEnvelope("1.2", Map.of("type", "click"))).calls().get(0);

        final var patches = page.handleResult(new BrowserResult(call.id(), null, "no such hook"));
        assertTrue(patches.patches().get(0).html().contains("<span>error:no such hook</span>"));
        assertTrue(page.zone().isStable());
    }

    @Test
    void customEventDetailReachesTheHandler() {
        final var page = page(Measure.class, true, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));

        final var patches = page.handleEvent(new EventEnvelope("1.1", Map.of("type", "point-picked", "detail", Map.of("index", 3))));
        assertTrue(patches.patches().get(0).html().contains("<i>3</i>"));
        assertTrue(patches.calls().isEmpty());
    }

    @Test
    void callsIssuedDuringTheFirstRenderTravelWithTheDocumentWithoutStallingIt() {
        final var pushed = new CopyOnWriteArrayList<PatchSet>();
        final var page = page(Viewport.class, true, pushed::add);

        final var started = System.nanoTime();
        final var html = page.renderInitial(Duration.ofSeconds(5));
        final var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0, "render waited " + elapsed);
        assertTrue(html.contains("<b>?</b>"), html);
        assertEquals(1, pushed.size());
        assertTrue(pushed.get(0).patches().isEmpty());
        assertEquals("viewport", pushed.get(0).calls().get(0).target());
        assertNull(pushed.get(0).calls().get(0).viewId());
        assertFalse(page.zone().isStable(), "pending once the document is out");

        page.handleResult(new BrowserResult(pushed.get(0).calls().get(0).id(), Map.of("width", 800), null));
        assertTrue(page.zone().isStable());
        assertTrue(page.renderFull().patches().get(0).html().contains("<b>{width=800}</b>"));
    }

    @Test
    void destroyingThePageCancelsOutstandingCalls() {
        final var page = page(Measure.class, true, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));
        page.handleEvent(new EventEnvelope("1.2", Map.of("type", "click")));

        page.destroy();
        assertTrue(Measure.last.isCancelled());
        assertFalse(page.zone().pendingTasks().hasPending());
    }

    @Test
    void callsAreRefusedWhenEventsAreNotLive() {
        final var pushed = new CopyOnWriteArrayList<PatchSet>();
        final var page = page(Viewport.class, false, pushed::add);

        final var html = page.renderInitial(Duration.ofSeconds(1));
        assertTrue(html.contains("error:"), html);
        assertTrue(html.contains("transport is off"), html);
        assertTrue(pushed.isEmpty());
        assertTrue(page.zone().isStable());
    }

    @Test
    void unknownResultIdsAreRejected() {
        final var page = page(Measure.class, true, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> page.handleResult(new BrowserResult("99", null, null)));
    }

    @Test
    void hookTargetsNeedACurrentComponent() {
        final var page = page(Measure.class, true, PatchSink.NONE);
        page.renderInitial(Duration.ofSeconds(1));
        assertThrows(IllegalStateException.class, () -> page.runTurn(() -> Browser.current().call("spark.size")));
        assertThrows(IllegalStateException.class, () -> Browser.current());
    }
}
