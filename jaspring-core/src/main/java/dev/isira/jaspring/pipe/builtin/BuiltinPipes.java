package dev.isira.jaspring.pipe.builtin;

import java.util.Map;

import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.pipe.PipeTransform;

/// Angular's built-in pipes, registered under their Angular names.
public final class BuiltinPipes {

    private BuiltinPipes() {}

    public static Map<String, PipeTransform> all() {
        return Map.ofEntries(
                Map.entry("uppercase", new CasePipes.Upper()),
                Map.entry("lowercase", new CasePipes.Lower()),
                Map.entry("titlecase", new CasePipes.Title()),
                Map.entry("json", new JsonPipe()),
                Map.entry("slice", new SlicePipe()),
                Map.entry("keyvalue", new KeyValuePipe()),
                Map.entry("number", new NumberPipes.Decimal()),
                Map.entry("percent", new NumberPipes.Percent()),
                Map.entry("currency", new NumberPipes.Currency()),
                Map.entry("date", new DatePipe()),
                Map.entry("async", new AsyncPipe()),
                Map.entry("i18nSelect", new I18nPipes.Select()),
                Map.entry("i18nPlural", new I18nPipes.Plural()));
    }

    public static void registerAll(ComponentRegistry registry) {
        all().forEach(registry::registerPipe);
    }
}
