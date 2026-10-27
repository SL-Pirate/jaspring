package dev.isira.jaspring.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.browser.ClosureManifest.Closure;
import dev.isira.jaspring.browser.ClosureManifest.Timing;
import dev.isira.jaspring.browser.Geolocation.Position;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.ComponentFactory;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.live.BrowserResult;
import dev.isira.jaspring.live.EventEnvelope;
import dev.isira.jaspring.live.LivePage;
import dev.isira.jaspring.live.PageSpec;
import dev.isira.jaspring.live.PatchSink;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.template.compile.TemplateCompiler;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;
import dev.isira.jaspring.zone.Zone;

class PlatformTest {

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

    record Spot(String city, int zoom) {}

    @Component(selector = "app-geo", template = "<button (click)=\"locate()\">l</button><button (click)=\"async()\">a</button>"
            + "<button (click)=\"captureThis()\">t</button><button (click)=\"captureList()\">c</button><b>{{ status() }}</b>")
    public static class Geo {
        public final WritableSignal<String> status = Signals.signal("?");
        public String field = "f";

        /// One lambda site shared by the component and the test, so both see the same synthetic method.
        static BrowserTask<Spot> spotTask(String city, int zoom) {
            return client -> new Spot(city + client.location().hash(), zoom);
        }

        static BrowserTask<BrowserPromise<Position>> positionTask() {
            return client -> client.geolocation().current();
        }

        public void locate() {
            Platform.onBrowser(spotTask("Colombo", 3)).whenComplete((spot, error) ->
                    status.set(error == null ? spot.city() + "/" + spot.zoom() : "error:" + error.getMessage()));
        }

        public void async() {
            Platform.onBrowserAsync(Position.class, positionTask()).whenComplete((position, error) ->
                    status.set(error == null ? String.valueOf(position.latitude()) : "error:" + error.getMessage()));
        }

        public void captureThis() {
            Platform.onBrowser(client -> field + client.location().hash());
        }

        public void captureList() {
            final var list = List.of("a");
            Platform.onBrowser(client -> list.size() + client.location().hash());
        }
    }

    static Closure closureFor(BrowserTask<?> task, String returnType, boolean promise) {
        final var lambda = ClosureCalls.serialized(task);
        final var id = ClosureIds.of(lambda.getImplClass(), lambda.getImplMethodName(), lambda.getImplMethodSignature());
        return new Closure(id, lambda.getImplClass().replace('/', '.'), lambda.getImplMethodName(), lambda.getImplMethodSignature(),
                List.of(), returnType, promise, Timing.EVENT, "locate", null);
    }

    static LivePage page(ClosureManifest manifest) {
        return new LivePage("p1", "s1", PageSpec.of(Geo.class), registry(Geo.class), SCHEMA, Zone.Mode.ZONE, true,
                PatchSink.NONE, manifest);
    }

    /// Handler failures surface wrapped by the expression evaluator; the closure error is the root cause.
    static Throwable rootCause(Throwable thrown) {
        var root = thrown;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }

    static ClosureManifest manifest() {
        return new ClosureManifest("Oracle GraalVM 25.2.4", null, List.of(
                closureFor(Geo.spotTask("x", 0), Spot.class.getName(), false),
                closureFor(Geo.positionTask(), Position.class.getName(), true)));
    }

    @Test
    void onBrowserSendsAWasmCallWithTheClosureIdAndCapturedValuesAndConvertsTheAnswer() {
        final var page = page(manifest());
        page.renderInitial(Duration.ofSeconds(1));

        final var reply = page.handleEvent(new EventEnvelope("1.1", Map.of("type", "click")));
        final var call = reply.calls().get(0);
        assertEquals("wasm", call.target());
        assertEquals(1L, call.viewId());
        final var expectedId = closureFor(Geo.spotTask("x", 0), Spot.class.getName(), false).id();
        assertEquals(List.of(expectedId, 1L, List.of("Colombo", 3)), call.args());

        final var patched = page.handleResult(new BrowserResult(call.id(), Map.of("city", "Colombo#top", "zoom", 3), null));
        assertTrue(patched.patches().get(0).html().contains("<b>Colombo#top/3</b>"), patched.toString());
    }

    @Test
    void asyncClosuresUseTheTypeTokenToConvertTheAnswer() {
        final var page = page(manifest());
        page.renderInitial(Duration.ofSeconds(1));

        final var call = page.handleEvent(new EventEnvelope("1.2", Map.of("type", "click"))).calls().get(0);
        final var patched = page.handleResult(new BrowserResult(call.id(),
                Map.of("latitude", 6.9, "longitude", 79.8, "timestamp", 1L), null));
        assertTrue(patched.patches().get(0).html().contains("<b>6.9</b>"), patched.toString());
    }

    @Test
    void closuresThatCaptureThisOrErasedCollectionsAreRejectedBeforeAnythingIsSent() {
        final var page = page(manifest());
        page.renderInitial(Duration.ofSeconds(1));

        final var thisCapture = rootCause(assertThrows(RuntimeException.class,
                () -> page.handleEvent(new EventEnvelope("1.3", Map.of("type", "click")))));
        assertEquals(IllegalArgumentException.class, thisCapture.getClass());
        assertTrue(thisCapture.getMessage().contains("uses instance members"), thisCapture.getMessage());
        assertTrue(thisCapture.getMessage().contains("PlatformTest$Geo.lambda$captureThis$"), thisCapture.getMessage());

        final var listCapture = rootCause(assertThrows(RuntimeException.class,
                () -> page.handleEvent(new EventEnvelope("1.4", Map.of("type", "click")))));
        assertEquals(IllegalArgumentException.class, listCapture.getClass());
        assertTrue(listCapture.getMessage().contains("declare a record"), listCapture.getMessage());
        assertEquals(0, page.zone().pendingTasks().count(), "nothing was sent or left pending");
    }

    @Test
    void missingOrStaleBuildsFailWithARebuildHint() {
        final var noBuild = page(ClosureManifest.EMPTY);
        noBuild.renderInitial(Duration.ofSeconds(1));
        final var missing = rootCause(assertThrows(RuntimeException.class,
                () -> noBuild.handleEvent(new EventEnvelope("1.1", Map.of("type", "click")))));
        assertEquals(BrowserClosuresUnavailableException.class, missing.getClass());
        assertTrue(missing.getMessage().contains("Gradle plugin"), missing.getMessage());

        final var otherBuild = page(new ClosureManifest("x", null, List.of(closureFor(Geo.positionTask(), Position.class.getName(), true))));
        otherBuild.renderInitial(Duration.ofSeconds(1));
        final var stale = rootCause(assertThrows(RuntimeException.class,
                () -> otherBuild.handleEvent(new EventEnvelope("1.1", Map.of("type", "click")))));
        assertEquals(BrowserClosuresUnavailableException.class, stale.getClass());
        assertTrue(stale.getMessage().contains("Stale"), stale.getMessage());
        assertTrue(stale.getMessage().contains("lambda$spotTask$"), stale.getMessage());
    }

    @Test
    void promiseShapeMustMatchTheEntryPoint() {
        final var page = page(manifest());
        page.renderInitial(Duration.ofSeconds(1));
        final var wrong = assertThrows(IllegalArgumentException.class, () -> page.runTurn(() ->
                dev.isira.jaspring.view.ComponentView.runAs(page.tree().root(), () -> Platform.onBrowser(Geo.positionTask()))));
        assertTrue(wrong.getMessage().contains("onBrowserAsync"), wrong.getMessage());
    }

    @Test
    void manifestAnswersTimingQuestionsPerComponent() {
        final var manifest = new ClosureManifest("x", null, List.of(
                new Closure("1", Geo.class.getName(), "lambda$new$0", "()V", List.of(), "void", false, Timing.MOUNT, "<init>", null),
                new Closure("2", Geo.class.getName(), "lambda$locate$0", "()V", List.of(), "void", false, Timing.EVENT, "locate", null)));
        assertTrue(manifest.hasMountClosures(Geo.class));
        assertTrue(manifest.hasAnyClosure(Geo.class));
        assertFalse(manifest.hasAnyClosure(Spot.class));
        assertEquals(java.util.Set.of("locate"), manifest.eventMethods(Geo.class));
        assertTrue(ClosureManifest.EMPTY.isEmpty());
    }
}
