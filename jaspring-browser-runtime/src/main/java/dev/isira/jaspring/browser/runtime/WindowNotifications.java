package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.BrowserPromise;
import dev.isira.jaspring.browser.Notifications;

final class WindowNotifications implements Notifications {

    @Override
    public PermissionState permission() {
        return state(permissionName());
    }

    @Override
    public BrowserPromise<PermissionState> requestPermission() {
        return new JsPromise<>(request(), raw -> state(Decoders.string(raw, "state")));
    }

    @Override
    public void show(String title, NotificationOptions options) {
        show(title, options.body(), options.icon(), options.tag());
    }

    static PermissionState state(String name) {
        return switch (name) {
            case "granted" -> PermissionState.GRANTED;
            case "denied" -> PermissionState.DENIED;
            default -> PermissionState.PROMPT;
        };
    }

    @JS(value = "return typeof Notification === 'undefined' ? 'denied' : Notification.permission;")
    @JS.Coerce
    private static native String permissionName();

    @JS(value = "if (typeof Notification === 'undefined') return Promise.resolve({ state: 'denied' }); return Notification.requestPermission().then(function (s) { return { state: s }; });")
    private static native JSObject request();

    @JS(args = {"title", "body", "icon", "tag"}, value = """
            if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
            var options = {};
            if (body !== null) options.body = body;
            if (icon !== null) options.icon = icon;
            if (tag !== null) options.tag = tag;
            new Notification(title, options);""")
    @JS.Coerce
    private static native void show(String title, String body, String icon, String tag);
}
