package dev.isira.jaspring.pipe.builtin;

import java.util.Locale;

import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.eval.Js;

final class CasePipes {

    private CasePipes() {}

    static final class Upper implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            return Js.isNullish(value) ? null : Js.toJsString(value).toUpperCase(Locale.ROOT);
        }
    }

    static final class Lower implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            return Js.isNullish(value) ? null : Js.toJsString(value).toLowerCase(Locale.ROOT);
        }
    }

    /// Like Angular: the first letter of every whitespace-separated word is upper-cased, the rest lower-cased.
    static final class Title implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            if (Js.isNullish(value)) return null;
            final var text = Js.toJsString(value);
            final var out = new StringBuilder(text.length());
            var startOfWord = true;
            for (var i = 0; i < text.length(); i++) {
                final var c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    startOfWord = true;
                    out.append(c);
                } else if (startOfWord) {
                    out.append(Character.toUpperCase(c));
                    startOfWord = false;
                } else {
                    out.append(Character.toLowerCase(c));
                }
            }
            return out.toString();
        }
    }
}
