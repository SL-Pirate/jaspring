package dev.isira.jaspring.browser;

import dev.isira.jaspring.zone.ZoneTask;

/// Runs Java closures in the browser. Call it inside a turn with a component current: in a constructor, a
/// lifecycle hook, an event handler or an output handler. The closure body is compiled to WebAssembly by the
/// Jaspring Gradle plugin; its result comes back over the live transport and completes the task inside a turn.
public final class Platform {

    private Platform() {
    }

    /// Runs `task` in the browser and completes with its return value, converted to `R` from JSON.
    public static <R> ZoneTask<R> onBrowser(BrowserTask<R> task) {
        return Browser.current().closure(task, null);
    }

    /// For closures that end in a browser promise (geolocation, clipboard, permissions). `type` names the promise's
    /// value type, which erasure hides from both the server and the build.
    public static <R> ZoneTask<R> onBrowserAsync(Class<R> type, BrowserTask<BrowserPromise<R>> task) {
        return Browser.current().closure(task, type);
    }
}
