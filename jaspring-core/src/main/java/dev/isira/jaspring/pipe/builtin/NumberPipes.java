package dev.isira.jaspring.pipe.builtin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.isira.jaspring.pipe.LocaleContext;
import dev.isira.jaspring.pipe.PipeTransform;
import dev.isira.jaspring.template.eval.Js;

/// `number`, `percent` and `currency` with Angular's `digitsInfo` (`minInt.minFrac-maxFrac`) and locale arguments.
/// Formatting uses `java.text`, so grouping and symbols come from the JDK's locale data rather than CLDR as shipped by Angular.
final class NumberPipes {

    private static final Pattern DIGITS_INFO = Pattern.compile("^(\\d+)?\\.((\\d+)(-(\\d+))?)?$");

    private NumberPipes() {}

    record Digits(int minInteger, int minFraction, int maxFraction) {

        static Digits parse(Object info, Digits defaults) {
            if (Js.isNullish(info)) return defaults;
            final var matcher = DIGITS_INFO.matcher(Js.toJsString(info));
            if (!matcher.matches()) {
                throw new IllegalArgumentException(Js.toJsString(info) + " is not a valid digit info");
            }
            final var minInteger = matcher.group(1) == null ? defaults.minInteger : Integer.parseInt(matcher.group(1));
            final var minFraction = matcher.group(3) == null ? defaults.minFraction : Integer.parseInt(matcher.group(3));
            final var maxFraction = matcher.group(5) == null ? Math.max(minFraction, defaults.maxFraction) : Integer.parseInt(matcher.group(5));
            if (minFraction > maxFraction) {
                throw new IllegalArgumentException("The minimum number of digits after fraction (" + minFraction
                        + ") is higher than the maximum (" + maxFraction + ").");
            }
            return new Digits(minInteger, minFraction, maxFraction);
        }
    }

    static String format(Object value, Digits digits, Locale locale, String prefixSymbol, boolean percent) {
        final var number = Js.toNumber(value);
        if (Double.isNaN(number.doubleValue())) {
            throw new IllegalArgumentException(Js.toJsString(value) + " is not a number");
        }
        var decimal = number instanceof BigDecimal b ? b : new BigDecimal(number.toString());
        if (percent) decimal = decimal.multiply(BigDecimal.valueOf(100));
        decimal = decimal.setScale(digits.maxFraction(), RoundingMode.HALF_EVEN);
        final var format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale));
        format.setMinimumIntegerDigits(digits.minInteger());
        format.setMinimumFractionDigits(digits.minFraction());
        format.setMaximumFractionDigits(digits.maxFraction());
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        final var text = format.format(decimal);
        if (percent) return text + "%";
        if (prefixSymbol == null) return text;
        return text.startsWith("-") ? "-" + prefixSymbol + text.substring(1) : prefixSymbol + text;
    }

    static final class Decimal implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            if (Js.isNullish(value)) return null;
            final var digits = Digits.parse(args.length > 0 ? args[0] : null, new Digits(1, 0, 3));
            return format(value, digits, LocaleContext.parse(args.length > 1 ? Js.toJsString(args[1]) : null), null, false);
        }
    }

    static final class Percent implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            if (Js.isNullish(value)) return null;
            final var digits = Digits.parse(args.length > 0 ? args[0] : null, new Digits(1, 0, 0));
            return format(value, digits, LocaleContext.parse(args.length > 1 ? Js.toJsString(args[1]) : null), null, true);
        }
    }

    /// `value | currency:code:display:digitsInfo:locale`; `display` is `code`, `symbol` (default), `symbol-narrow`,
    /// a custom string, or a boolean for the legacy symbol/code switch.
    static final class Currency implements PipeTransform {
        @Override
        public Object transform(Object value, Object... args) {
            if (Js.isNullish(value)) return null;
            final var code = args.length > 0 && !Js.isNullish(args[0]) ? Js.toJsString(args[0]) : "USD";
            final var display = args.length > 1 && !Js.isNullish(args[1]) ? args[1] : "symbol";
            final var locale = LocaleContext.parse(args.length > 3 ? Js.toJsString(args[3]) : null);
            final var currency = java.util.Currency.getInstance(code);
            final var defaults = new Digits(1, currency.getDefaultFractionDigits(), currency.getDefaultFractionDigits());
            final var digits = Digits.parse(args.length > 2 ? args[2] : null, defaults);
            final var symbol = switch (display) {
                case Boolean b -> b ? symbolOf(currency, locale) : code;
                case String s when s.equals("code") -> code;
                case String s when s.equals("symbol") || s.equals("symbol-narrow") -> symbolOf(currency, locale);
                default -> Js.toJsString(display);
            };
            final var text = format(value, digits, locale, "", false);
            return text.startsWith("-") ? "-" + symbol + text.substring(1) : symbol + text;
        }

        private static String symbolOf(java.util.Currency currency, Locale locale) {
            final var symbol = currency.getSymbol(locale);
            return symbol.equals(currency.getCurrencyCode()) && currency.getCurrencyCode().equals("USD") ? "$" : symbol;
        }
    }
}
