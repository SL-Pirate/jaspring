package dev.isira.jaspring.browser;

/// `navigator.geolocation`. The browser asks the user for permission on first use.
public interface Geolocation {

    BrowserPromise<Position> current();

    BrowserPromise<Position> current(GeolocationOptions options);

    /// Accuracy, altitude, heading and speed are null when the device does not report them.
    record Position(double latitude, double longitude, Double accuracy, Double altitude, Double heading, Double speed, long timestamp) {}

    record GeolocationOptions(boolean enableHighAccuracy, int timeoutMillis, int maximumAgeMillis) {

        public static final GeolocationOptions DEFAULT = new GeolocationOptions(false, 10_000, 0);
    }
}
