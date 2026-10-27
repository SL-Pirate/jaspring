package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.BrowserPromise;
import dev.isira.jaspring.browser.Geolocation;

final class NavigatorGeolocation implements Geolocation {

    @Override
    public BrowserPromise<Position> current() {
        return current(GeolocationOptions.DEFAULT);
    }

    @Override
    public BrowserPromise<Position> current(GeolocationOptions options) {
        final var promise = request(options.enableHighAccuracy(), options.timeoutMillis(), options.maximumAgeMillis());
        return new JsPromise<>(promise, NavigatorGeolocation::decode);
    }

    static Position decode(JSObject position) {
        return new Position(Decoders.doubleValue(position, "latitude"), Decoders.doubleValue(position, "longitude"),
                optional(position, "accuracy"), optional(position, "altitude"), optional(position, "heading"), optional(position, "speed"),
                Decoders.longValue(position, "timestamp"));
    }

    private static Double optional(JSObject position, String key) {
        return Decoders.isNull(position, key) ? null : Decoders.doubleValue(position, key);
    }

    /// Resolves with a plain object: the browser's `GeolocationPosition` is not serializable and its coordinates
    /// live on a prototype, so they are copied out here.
    @JS(args = {"highAccuracy", "timeout", "maximumAge"}, value = """
            return new Promise(function (resolve, reject) {
              if (!navigator.geolocation) { reject(new Error('Geolocation is not available')); return; }
              navigator.geolocation.getCurrentPosition(function (p) {
                var c = p.coords;
                resolve({ latitude: c.latitude, longitude: c.longitude, accuracy: c.accuracy, altitude: c.altitude,
                          heading: c.heading, speed: c.speed, timestamp: p.timestamp });
              }, function (e) { reject(new Error(e && e.message ? e.message : 'Geolocation failed')); },
              { enableHighAccuracy: highAccuracy, timeout: timeout, maximumAge: maximumAge });
            });""")
    @JS.Coerce
    private static native JSObject request(boolean highAccuracy, int timeout, int maximumAge);
}
