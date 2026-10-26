package dev.isira.jaspring.live;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/// The `$event` of a DOM handler on the server: the safe subset the shim sent, readable as `$event.target.value`.
/// `preventDefault` and `stopPropagation` are no-ops because the browser already applied the static flags.
public final class DomEvent extends LinkedHashMap<String, Object> {

    public DomEvent(Map<String, Object> data) {
        super(data);
        put("preventDefault", (dev.isira.jaspring.template.eval.JsFunction) args -> null);
        put("stopPropagation", (dev.isira.jaspring.template.eval.JsFunction) args -> null);
    }

    public String type() {
        return String.valueOf(get("type"));
    }

    public Map<String, Object> target() {
        final var target = get("target");
        return target instanceof Map<?, ?> map ? castMap(map) : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
