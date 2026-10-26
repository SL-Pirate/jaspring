package dev.isira.jaspring.template.eval;

import java.util.regex.Pattern;

/// A `/body/flags` literal. Exposes `test` like JavaScript's RegExp.
public record JsRegExp(Pattern pattern) {

    public static JsRegExp of(String body, String flags) {
        var javaFlags = 0;
        if (flags != null) {
            if (flags.contains("i")) javaFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            if (flags.contains("m")) javaFlags |= Pattern.MULTILINE;
            if (flags.contains("s")) javaFlags |= Pattern.DOTALL;
        }
        return new JsRegExp(Pattern.compile(body, javaFlags));
    }

    public boolean test(Object value) {
        return value != null && pattern.matcher(Js.toJsString(value)).find();
    }
}
