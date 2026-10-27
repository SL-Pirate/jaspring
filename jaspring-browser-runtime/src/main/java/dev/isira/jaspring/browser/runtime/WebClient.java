package dev.isira.jaspring.browser.runtime;

import dev.isira.jaspring.browser.BrowserClient;
import dev.isira.jaspring.browser.Clipboard;
import dev.isira.jaspring.browser.Cookies;
import dev.isira.jaspring.browser.Geolocation;
import dev.isira.jaspring.browser.HostElement;
import dev.isira.jaspring.browser.Location;
import dev.isira.jaspring.browser.Notifications;
import dev.isira.jaspring.browser.Storage;
import dev.isira.jaspring.browser.Vibration;

/// The facade a closure receives. The host element is looked up lazily by view id.
public final class WebClient implements BrowserClient {

    private final String viewId;
    private HostElement host;

    private WebClient(String viewId) {
        this.viewId = viewId;
    }

    public static WebClient forHost(String viewId) {
        return new WebClient(viewId);
    }

    @Override
    public HostElement host() {
        if (host == null) {
            final var element = WasmBridge.querySelector("[data-jsp-v=\"" + viewId + "\"]");
            if (WasmBridge.isNullish(element)) throw new IllegalStateException("Host element of view " + viewId + " is not in the document");
            host = new DomElement.Host(element, Long.parseLong(viewId));
        }
        return host;
    }

    @Override
    public Storage localStorage() {
        return new WebStorage(true);
    }

    @Override
    public Storage sessionStorage() {
        return new WebStorage(false);
    }

    @Override
    public Cookies cookies() {
        return new DocumentCookies();
    }

    @Override
    public Location location() {
        return new WindowLocation();
    }

    @Override
    public Geolocation geolocation() {
        return new NavigatorGeolocation();
    }

    @Override
    public Clipboard clipboard() {
        return new NavigatorClipboard();
    }

    @Override
    public Notifications notifications() {
        return new WindowNotifications();
    }

    @Override
    public Vibration vibration() {
        return new NavigatorVibration();
    }
}
