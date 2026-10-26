package dev.isira.jaspring.pipe.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.pipe.LocaleContext;
import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.signals.Signals;

class BuiltinPipesTest {

    private static final Map<String, PipeTransform> PIPES = BuiltinPipes.all();

    private static Object pipe(String name, Object value, Object... args) {
        return PIPES.get(name).transform(value, args);
    }

    @Test
    void casePipes() {
        assertEquals("ABC", pipe("uppercase", "abc"));
        assertEquals("abc", pipe("lowercase", "ABC"));
        assertEquals("Hello World  Foo-bar", pipe("titlecase", "hELLO wORLD  foo-BAR"));
        assertNull(pipe("uppercase", null));
    }

    @Test
    void numberPercentAndCurrencyFollowDigitsInfo() {
        assertEquals("3.142", pipe("number", 3.14159));
        assertEquals("1,234.5", pipe("number", 1234.5));
        assertEquals("0,003.14", pipe("number", 3.14159, "4.2-2"));
        assertEquals("3.1", pipe("number", 3.1, "1.1-3"));
        assertEquals("26%", pipe("percent", 0.259));
        assertEquals("25.9%", pipe("percent", 0.259, "1.1-1"));
        assertEquals("$1,234.50", pipe("currency", 1234.5));
        assertEquals("EUR1,234.50", pipe("currency", 1234.5, "EUR", "code"));
        assertEquals("€1,234.50", pipe("currency", 1234.5, "EUR"));
        assertEquals("-$5.00", pipe("currency", -5));
        assertEquals("1.234,5", LocaleContext.runWith(Locale.GERMANY, () -> pipe("number", 1234.5)));
        assertEquals("1.234,5", pipe("number", 1234.5, null, "de-DE"));
        assertThrows(IllegalArgumentException.class, () -> pipe("number", "abc"));
    }

    @Test
    void datePipeFormatsNamedAndCustomPatterns() {
        final var date = ZonedDateTime.of(2024, 3, 9, 14, 5, 7, 0, ZoneId.of("UTC"));
        assertEquals("Mar 9, 2024", pipe("date", date));
        assertEquals("3/9/24, 2:05 PM", pipe("date", date, "short"));
        assertEquals("Saturday, March 9, 2024", pipe("date", date, "fullDate"));
        assertEquals("2024-03-09 14:05:07", pipe("date", date, "yyyy-MM-dd HH:mm:ss"));
        assertEquals("09.03.2024", pipe("date", LocalDate.of(2024, 3, 9), "dd.MM.yyyy"));
        assertEquals("15:05 +0100", pipe("date", date, "HH:mm Z", "+0100"));
        assertEquals("Jan 1, 1970", pipe("date", 0L, "mediumDate", "UTC"));
        assertEquals("Mar 9, 2024", pipe("date", "2024-03-09", "mediumDate"));
        assertEquals("14:05", pipe("date", LocalDateTime.of(2024, 3, 9, 14, 5), "HH:mm"));
        assertEquals("9. März 2024", pipe("date", date, "longDate", "UTC", "de"));
    }

    @Test
    void sliceKeyValueAndJson() {
        assertEquals(List.of("b", "c"), pipe("slice", List.of("a", "b", "c"), 1));
        assertEquals("bc", pipe("slice", "abcd", 1, -1));
        assertEquals(List.of("c"), pipe("slice", new String[] {"a", "b", "c"}, -1));

        final var map = new LinkedHashMap<String, Object>();
        map.put("b", 2);
        map.put("a", 1);
        assertEquals(List.of(new KeyValuePipe.KeyValue("a", 1), new KeyValuePipe.KeyValue("b", 2)), pipe("keyvalue", map));
        assertEquals(List.of(new KeyValuePipe.KeyValue("b", 2), new KeyValuePipe.KeyValue("a", 1)), pipe("keyvalue", map, (Object) null));

        assertEquals("{\n  \"name\": \"Ann\",\n  \"tags\": [\n    \"x\",\n    1\n  ],\n  \"nested\": {\n    \"on\": true\n  }\n}",
                pipe("json", Map.of("name", "Ann", "tags", List.of("x", 1), "nested", Map.of("on", true)).entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(k -> k.equals("name") ? 0 : k.equals("tags") ? 1 : 2)))
                        .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll)));
        assertEquals("{\n  \"x\": 1,\n  \"y\": \"q\\\"\"\n}", pipe("json", new Point(1, "q\"")));
    }

    public record Point(int x, String y) {}

    @Test
    void asyncReadsSignalsAndFutures() {
        assertEquals(3, pipe("async", Signals.signal(3)));
        assertEquals("done", pipe("async", CompletableFuture.completedFuture("done")));
        assertNull(pipe("async", null));
    }

    @Test
    void i18nSelectAndPlural() {
        final var genders = Map.of("male", "he", "female", "she", "other", "they");
        assertEquals("she", pipe("i18nSelect", "female", genders));
        assertEquals("they", pipe("i18nSelect", "x", genders));
        final var counts = Map.of("=0", "none", "=1", "one item", "other", "# items");
        assertEquals("none", pipe("i18nPlural", 0, counts));
        assertEquals("one item", pipe("i18nPlural", 1, counts));
        assertEquals("7 items", pipe("i18nPlural", 7, counts));
    }
}
