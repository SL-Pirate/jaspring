package dev.isira.jaspring.pipe.builtin;

import java.util.Map;

import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.eval.Js;

final class I18nPipes {

    private I18nPipes() {}

    /// `value | i18nSelect:{male: 'he', female: 'she', other: 'they'}`.
    static final class Select implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            if (Js.isNullish(value)) return "";
            if (args.length == 0 || !(args[0] instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException("i18nSelect pipe expects a map argument");
            }
            final var key = Js.toJsString(value);
            if (mapping.containsKey(key)) return Js.toJsString(mapping.get(key));
            if (mapping.containsKey("other")) return Js.toJsString(mapping.get("other"));
            return "";
        }
    }

    /// `count | i18nPlural:{'=0': 'none', '=1': 'one', 'other': '# items'}`; `#` becomes the number.
    static final class Plural implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            if (Js.isNullish(value)) return "";
            if (args.length == 0 || !(args[0] instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException("i18nPlural pipe expects a map argument");
            }
            final var number = Js.toNumber(value);
            final var exact = "=" + Js.toJsString(number);
            final String text;
            if (mapping.containsKey(exact)) text = Js.toJsString(mapping.get(exact));
            else {
                final var category = number.doubleValue() == 1 ? "one" : "other";
                final var fallback = mapping.containsKey("other") ? mapping.get("other") : "";
                text = Js.toJsString(mapping.containsKey(category) ? mapping.get(category) : fallback);
            }
            return text.replace("#", Js.toJsString(number));
        }
    }
}
