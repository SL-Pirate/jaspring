package dev.isira.jaspring.browser;

/// `navigator.vibrate`.
public interface Vibration {

    /// Returns false when the device or browser does not vibrate.
    boolean vibrate(int... patternMillis);
}
