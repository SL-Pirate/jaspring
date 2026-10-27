package dev.isira.jaspring.browser;

/// What a browser closure can reach. Every method is implemented in the browser by `jaspring-browser-runtime`;
/// nothing here touches the server.
public interface BrowserClient {

    /// The component's own host element (`data-jsp-v`). DOM writes are transient: the next patch morphs the host
    /// from server HTML, so lasting state belongs in signals.
    HostElement host();

    Storage localStorage();

    Storage sessionStorage();

    Cookies cookies();

    Location location();

    Geolocation geolocation();

    Clipboard clipboard();

    Notifications notifications();

    Vibration vibration();
}
