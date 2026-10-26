package dev.isira.jaspring.pipe.builtin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import dev.isira.jaspring.pipe.LocaleContext;
import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.eval.Js;

/// `value | date:format:timezone:locale`. Accepts `java.time` values, `Date`, epoch milliseconds and ISO strings.
/// Angular's named formats and most of its tokens map onto `DateTimeFormatter` patterns.
final class DatePipe implements PipeTransform {

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("short", "M/d/yy, h:mm a"), Map.entry("medium", "MMM d, y, h:mm:ss a"),
            Map.entry("long", "MMMM d, y, h:mm:ss a z"), Map.entry("full", "EEEE, MMMM d, y, h:mm:ss a zzzz"),
            Map.entry("shortDate", "M/d/yy"), Map.entry("mediumDate", "MMM d, y"), Map.entry("longDate", "MMMM d, y"),
            Map.entry("fullDate", "EEEE, MMMM d, y"), Map.entry("shortTime", "h:mm a"), Map.entry("mediumTime", "h:mm:ss a"),
            Map.entry("longTime", "h:mm:ss a z"), Map.entry("fullTime", "h:mm:ss a zzzz"));

    @Override
    public Object transform(Object value, Object... args) {
        if (Js.isNullish(value) || "".equals(value)) return null;
        final var format = args.length > 0 && !Js.isNullish(args[0]) ? Js.toJsString(args[0]) : "mediumDate";
        final var timezone = args.length > 1 && !Js.isNullish(args[1]) ? Js.toJsString(args[1]) : null;
        final var locale = LocaleContext.parse(args.length > 2 ? Js.toJsString(args[2]) : null);
        final var temporal = toTemporal(value, timezone);
        final var formatter = NAMED.containsKey(format) && !locale.getLanguage().equals("en")
                ? localized(format, locale)
                : DateTimeFormatter.ofPattern(javaPattern(NAMED.getOrDefault(format, format)), locale);
        // CLDR 42+ puts a narrow no-break space before AM/PM; a plain space is what Angular's data renders.
        return formatter.format(temporal).replace('\u202f', ' ');
    }

    // Angular's named formats are locale data; outside English the JDK's localized styles are the closest match.
    private static DateTimeFormatter localized(String name, Locale locale) {
        final var style = switch (name.replaceAll("Date$|Time$", "")) {
            case "short" -> java.time.format.FormatStyle.SHORT;
            case "medium" -> java.time.format.FormatStyle.MEDIUM;
            case "long" -> java.time.format.FormatStyle.LONG;
            default -> java.time.format.FormatStyle.FULL;
        };
        final var formatter = name.endsWith("Date") ? DateTimeFormatter.ofLocalizedDate(style)
                : name.endsWith("Time") ? DateTimeFormatter.ofLocalizedTime(style)
                : DateTimeFormatter.ofLocalizedDateTime(style);
        return formatter.withLocale(locale);
    }

    private static Temporal toTemporal(Object value, String timezone) {
        final var zone = timezone == null ? ZoneId.systemDefault() : parseZone(timezone);
        return switch (value) {
            case ZonedDateTime z -> timezone == null ? z : z.withZoneSameInstant(zone);
            case OffsetDateTime o -> timezone == null ? o : o.atZoneSameInstant(zone);
            case Instant i -> i.atZone(zone);
            case LocalDateTime l -> l.atZone(zone);
            case LocalDate d -> d.atStartOfDay(zone);
            case LocalTime t -> t.atDate(LocalDate.now(zone)).atZone(zone);
            case Date d -> d.toInstant().atZone(zone);
            case Number n -> Instant.ofEpochMilli(n.longValue()).atZone(zone);
            case CharSequence s -> parse(s.toString(), zone);
            default -> throw new IllegalArgumentException("Unable to convert \"" + value + "\" into a date for the date pipe");
        };
    }

    private static Temporal parse(String text, ZoneId zone) {
        try {
            return ZonedDateTime.parse(text).withZoneSameInstant(zone);
        } catch (DateTimeParseException ignored) { }
        try {
            return LocalDateTime.parse(text).atZone(zone);
        } catch (DateTimeParseException ignored) { }
        try {
            return LocalDate.parse(text).atStartOfDay(zone);
        } catch (DateTimeParseException ignored) { }
        if (text.matches("-?\\d+")) return Instant.ofEpochMilli(Long.parseLong(text)).atZone(zone);
        throw new IllegalArgumentException("Unable to convert \"" + text + "\" into a date for the date pipe");
    }

    private static ZoneId parseZone(String timezone) {
        if (timezone.matches("[+-]\\d{2}:?\\d{2}")) return ZoneOffset.of(timezone.length() == 5 ? timezone.substring(0, 3) + ":" + timezone.substring(3) : timezone);
        return ZoneId.of(timezone);
    }

    // Angular and Java share most tokens; the few that differ are rewritten here.
    static String javaPattern(String angular) {
        final var out = new StringBuilder();
        var i = 0;
        while (i < angular.length()) {
            final var c = angular.charAt(i);
            if (c == '\'') {
                final var close = angular.indexOf('\'', i + 1);
                out.append(angular, i, close < 0 ? angular.length() : close + 1);
                i = close < 0 ? angular.length() : close + 1;
                continue;
            }
            var run = 1;
            while (i + run < angular.length() && angular.charAt(i + run) == c) run++;
            final var token = String.valueOf(c).repeat(run);
            out.append(switch (token) {
                case "Z", "ZZ", "ZZZ" -> "xx";
                case "ZZZZ" -> "O";
                case "ZZZZZ" -> "xxx";
                case "z", "zz", "zzz" -> "z";
                case "zzzz" -> "zzzz";
                case "b", "bb", "bbb", "bbbb", "bbbbb" -> "B";
                case "B", "BB", "BBB", "BBBB", "BBBBB" -> "B";
                case "G", "GG", "GGG" -> "G";
                case "GGGG" -> "GGGG";
                case "GGGGG" -> "G";
                default -> Character.isLetter(c) ? token : token;
            });
            i += run;
        }
        return out.toString();
    }
}
