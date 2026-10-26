package dev.isira.jaspring.pipe.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.eval.Js;
import dev.isira.jaspring.template.eval.JsFunction;

/// `map | keyvalue` → a list of `{key, value}` sorted by key (numbers numerically, otherwise as strings),
/// or by the comparator passed as argument (`null` keeps insertion order, as in Angular).
final class KeyValuePipe implements PipeTransform {

    public record KeyValue(Object key, Object value) {}

    @Override
    public Object transform(Object value, Object... args) {
        if (Js.isNullish(value)) return null;
        final var entries = new ArrayList<KeyValue>();
        switch (value) {
            case Map<?, ?> map -> map.forEach((k, v) -> entries.add(new KeyValue(k, v)));
            case List<?> list -> {
                for (var i = 0; i < list.size(); i++) entries.add(new KeyValue((long) i, list.get(i)));
            }
            default -> throw new IllegalArgumentException("keyvalue pipe expects a Map or List");
        }
        if (args.length > 0 && args[0] == null) return entries;
        if (args.length > 0 && args[0] instanceof JsFunction comparator) {
            entries.sort((a, b) -> (int) Js.toDouble(comparator.call(a, b)));
            return entries;
        }
        entries.sort((a, b) -> {
            if (a.key() instanceof Number x && b.key() instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue());
            if (a.key() instanceof Boolean x && b.key() instanceof Boolean y) return Boolean.compare(x, y);
            return Js.toJsString(a.key()).compareTo(Js.toJsString(b.key()));
        });
        return entries;
    }
}
