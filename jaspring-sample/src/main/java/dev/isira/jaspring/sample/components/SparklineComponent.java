package dev.isira.jaspring.sample.components;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import dev.isira.jaspring.browser.Browser;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;

/// A canvas drawn by a client hook (static/sparkline.js). The hook reports clicks as a custom event, and the
/// server asks the browser for measurements through `Browser.call`.
@Component(selector = "app-sparkline", templateUrl = "sparkline.component.html", styles = """
        :host { @apply mb-6 block rounded-lg border border-gray-200 bg-white p-4; }
        """)
public class SparklineComponent {

    public final WritableSignal<List<Integer>> points = Signals.signal(List.of(3, 5, 2, 8, 6));
    public final WritableSignal<String> picked = Signals.signal("Click a point.");
    public final WritableSignal<String> status = Signals.signal("");

    public void addPoint() {
        points.update(current -> {
            final var next = new ArrayList<>(current);
            next.add(ThreadLocalRandom.current().nextInt(1, 10));
            return List.copyOf(next);
        });
    }

    public void pick(Number index) {
        final var value = points.get().get(index.intValue());
        picked.set("Picked point #" + (index.intValue() + 1) + " with value " + value);
    }

    public void measure() {
        final var browser = Browser.current();
        status.set("measuring...");
        browser.call("sparkline.size").whenComplete((size, error) ->
                status.set(error == null ? "canvas " + size : "canvas: " + error.getMessage()));
        browser.call("viewport").whenComplete((viewport, error) ->
                status.set(status.get() + (error == null ? ", viewport " + viewport : ", viewport: " + error.getMessage())));
    }
}
