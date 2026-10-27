package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import dev.isira.jaspring.browser.Vibration;

final class NavigatorVibration implements Vibration {

    @Override
    public boolean vibrate(int... patternMillis) {
        final var pattern = Encoders.array();
        for (final var millis : patternMillis) {
            Encoders.push(pattern, millis);
        }
        return vibrate(pattern);
    }

    @JS(args = {"pattern"}, value = "return typeof navigator.vibrate === 'function' ? navigator.vibrate(pattern) === true : false;")
    @JS.Coerce
    private static native boolean vibrate(JSObject pattern);
}
