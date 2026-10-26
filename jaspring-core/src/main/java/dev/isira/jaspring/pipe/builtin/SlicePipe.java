package dev.isira.jaspring.pipe.builtin;

import java.util.ArrayList;
import java.util.List;

import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.eval.Js;

/// `value | slice:start:end` with JavaScript's negative index rules, for lists, arrays and strings.
final class SlicePipe implements PipeTransform {

    @Override
    public Object transform(Object value, Object... args) {
        if (Js.isNullish(value)) return null;
        final var start = args.length > 0 ? (int) Js.toDouble(args[0]) : 0;
        final Integer end = args.length > 1 && !Js.isNullish(args[1]) ? (int) Js.toDouble(args[1]) : null;
        if (value instanceof CharSequence s) {
            final var text = s.toString();
            return text.substring(from(start, text.length()), to(end, text.length(), from(start, text.length())));
        }
        final List<?> list = value instanceof List<?> l ? l : value.getClass().isArray() ? Js.arrayAsList(value) : null;
        if (list == null) return value;
        final var begin = from(start, list.size());
        return new ArrayList<>(list.subList(begin, to(end, list.size(), begin)));
    }

    private static int from(int start, int size) {
        return Math.clamp(start < 0 ? size + start : start, 0, size);
    }

    private static int to(Integer end, int size, int begin) {
        if (end == null) return size;
        return Math.clamp(end < 0 ? size + end : end, begin, size);
    }
}
