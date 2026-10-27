package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

/// Builds JSON-shaped JavaScript values from Java scalars. Generated encoders call these; nothing is reflective.
public final class Encoders {

    private Encoders() {
    }

    @JS(value = "return {};")
    public static native JSObject object();

    @JS(value = "return [];")
    public static native JSObject array();

    @JS(args = {"object", "key", "value"}, value = "object[key] = value;")
    @JS.Coerce
    public static native void set(JSObject object, String key, String value);

    @JS(args = {"object", "key", "value"}, value = "object[key] = value;")
    @JS.Coerce
    public static native void set(JSObject object, String key, double value);

    @JS(args = {"object", "key", "value"}, value = "object[key] = value;")
    @JS.Coerce
    public static native void set(JSObject object, String key, boolean value);

    @JS(args = {"object", "key", "value"}, value = "object[key] = value;")
    public static native void set(JSObject object, String key, JSObject value);

    @JS(args = {"object", "key"}, value = "object[key] = null;")
    @JS.Coerce
    public static native void setNull(JSObject object, String key);

    @JS(args = {"array", "value"}, value = "array.push(value);")
    @JS.Coerce
    public static native void push(JSObject array, String value);

    @JS(args = {"array", "value"}, value = "array.push(value);")
    @JS.Coerce
    public static native void push(JSObject array, double value);

    @JS(args = {"array", "value"}, value = "array.push(value);")
    @JS.Coerce
    public static native void push(JSObject array, boolean value);

    @JS(args = {"array", "value"}, value = "array.push(value);")
    public static native void push(JSObject array, JSObject value);

    @JS(args = {"array"}, value = "array.push(null);")
    public static native void pushNull(JSObject array);

    /// Wraps a scalar as a JavaScript value so one method can hand any encoded value to an envelope.
    @JS(args = {"value"}, value = "return { v: value };")
    @JS.Coerce
    public static native JSObject boxed(String value);

    @JS(args = {"value"}, value = "return { v: value };")
    @JS.Coerce
    public static native JSObject boxed(double value);

    @JS(args = {"value"}, value = "return { v: value };")
    @JS.Coerce
    public static native JSObject boxed(boolean value);

    @JS(args = {"value"}, value = "return { v: value };")
    public static native JSObject boxed(JSObject value);

    @JS(value = "return { v: null };")
    public static native JSObject boxedNull();

    /// Stores an already boxed value under `key`, unboxing it: `object[key] = boxed.v`.
    @JS(args = {"object", "key", "boxed"}, value = "object[key] = boxed.v;")
    @JS.Coerce
    public static native void setBoxed(JSObject object, String key, JSObject boxed);

    public static JSObject boxedString(String value) {
        return value == null ? boxedNull() : boxed(value);
    }

    public static JSObject boxedNumber(Number value) {
        return value == null ? boxedNull() : boxed(value.doubleValue());
    }

    public static JSObject boxedBoolean(Boolean value) {
        return value == null ? boxedNull() : boxed(value.booleanValue());
    }

    public static JSObject boxedCharacter(Character value) {
        return value == null ? boxedNull() : boxed(String.valueOf(value));
    }

    public static JSObject boxedName(Enum<?> value) {
        return value == null ? boxedNull() : boxed(value.name());
    }
}
