package dev.isira.jaspring.sample.components;

import dev.isira.jaspring.browser.BrowserClosuresUnavailableException;
import dev.isira.jaspring.browser.Geolocation.Position;
import dev.isira.jaspring.browser.Platform;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;

/// Java closures that run in the browser as WebAssembly (`./gradlew :jaspring-sample:bootRun -Pjaspring.wasm=true`).
/// The theme is read while the page mounts, so this page preloads the runtime; the buttons run closures on demand.
@Component(selector = "app-browser", templateUrl = "browser.component.html", styles = """
        :host { @apply mb-6 block rounded-lg border border-gray-200 bg-white p-4; }
        """)
public class BrowserComponent implements OnInit {

    public final WritableSignal<String> theme = Signals.signal("reading...");
    public final WritableSignal<String> position = Signals.signal("");
    public final WritableSignal<String> clipboard = Signals.signal("");

    @Override
    public void ngOnInit() {
        try {
            Platform.onBrowser(client -> client.localStorage().get("theme").orElse("light"))
                    .whenComplete((value, error) -> theme.set(error == null ? value : explain(error)));
        } catch (BrowserClosuresUnavailableException e) {
            theme.set(explain(e));
        }
    }

    public void toggleTheme() {
        final var next = "dark".equals(theme.get()) ? "light" : "dark";
        try {
            Platform.onBrowser(client -> {
                client.localStorage().set("theme", next);
                client.host().toggleClass("dark", "dark".equals(next));
                return next;
            }).whenComplete((value, error) -> theme.set(error == null ? value : explain(error)));
        } catch (BrowserClosuresUnavailableException e) {
            theme.set(explain(e));
        }
    }

    public void locate() {
        position.set("asking the browser...");
        try {
            Platform.onBrowserAsync(Position.class, client -> client.geolocation().current())
                    .whenComplete((found, error) -> position.set(error == null
                            ? "%.4f, %.4f (±%.0f m)".formatted(found.latitude(), found.longitude(), found.accuracy() == null ? 0 : found.accuracy())
                            : explain(error)));
        } catch (BrowserClosuresUnavailableException e) {
            position.set(explain(e));
        }
    }

    public void copyPosition() {
        final var text = position.get();
        try {
            Platform.onBrowserAsync(Void.class, client -> client.clipboard().writeText(text))
                    .whenComplete((ignored, error) -> clipboard.set(error == null ? "copied" : explain(error)));
        } catch (BrowserClosuresUnavailableException e) {
            clipboard.set(explain(e));
        }
    }

    private static String explain(Throwable error) {
        return error instanceof BrowserClosuresUnavailableException ? "WebAssembly build not available: " + error.getMessage() : "error: " + error.getMessage();
    }
}
