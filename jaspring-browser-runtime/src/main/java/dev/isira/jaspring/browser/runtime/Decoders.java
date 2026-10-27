package dev.isira.jaspring.browser.runtime;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

/// Reads JSON-shaped JavaScript values into Java scalars. Generated decoders call these; nothing is reflective.
public final class Decoders {

    private Decoders() {
    }

    @JS(args = {"object", "key"}, value = "var v = object[key]; return v === null || v === undefined;")
    @JS.Coerce
    public static native boolean isNull(JSObject object, String key);

    @JS(args = {"object", "key"}, value = "return String(object[key]);")
    @JS.Coerce
    public static native String string(JSObject object, String key);

    @JS(args = {"object", "key"}, value = "return Number(object[key]) | 0;")
    @JS.Coerce
    public static native int integer(JSObject object, String key);

    @JS(args = {"object", "key"}, value = "return Math.trunc(Number(object[key]));")
    @JS.Coerce
    public static native double wholeNumber(JSObject object, String key);

    public static long longValue(JSObject object, String key) {
        return (long) wholeNumber(object, key);
    }

    @JS(args = {"object", "key"}, value = "return Number(object[key]);")
    @JS.Coerce
    public static native double doubleValue(JSObject object, String key);

    @JS(args = {"object", "key"}, value = "return object[key] === true || object[key] === 'true';")
    @JS.Coerce
    public static native boolean bool(JSObject object, String key);

    @JS(args = {"object", "key"}, value = "return object[key];")
    @JS.Coerce
    public static native JSObject object(JSObject object, String key);

    @JS(args = {"array"}, value = "return array ? array.length : 0;")
    @JS.Coerce
    public static native int length(JSObject array);

    @JS(args = {"object"}, value = "return object ? Object.keys(object) : [];")
    public static native JSObject keys(JSObject object);

    /// JavaScript arrays expose their items under the keys "0", "1", ... ; this spells that out for generated code.
    public static String index(int index) {
        return Integer.toString(index);
    }
}
