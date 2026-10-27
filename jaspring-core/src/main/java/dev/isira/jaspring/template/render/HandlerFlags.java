package dev.isira.jaspring.template.render;

import java.util.LinkedHashSet;
import java.util.Set;

import dev.isira.jaspring.template.expr.Expr;

/// Decides at render time what the browser must do when it dispatches an event, because the server
/// cannot answer synchronously: `preventDefault` when the handler ends in a literal `false` or calls
/// `$event.preventDefault()`, `stopPropagation` when it calls `$event.stopPropagation()`, and `wasm` when the
/// handler runs a browser closure, so the shim can warm the WebAssembly runtime on hover or focus.
public record HandlerFlags(boolean preventDefault, boolean stopPropagation, boolean wasm) {

    public static HandlerFlags analyze(Expr handler) {
        return analyze(handler, false);
    }

    public static HandlerFlags analyze(Expr handler, boolean wasm) {
        final var last = handler instanceof Expr.Chain chain && !chain.expressions().isEmpty()
                ? chain.expressions().getLast()
                : handler;
        final var returnsFalse = last instanceof Expr.LiteralPrimitive literal && Boolean.FALSE.equals(literal.value());
        return new HandlerFlags(returnsFalse || calls(handler, "preventDefault"), calls(handler, "stopPropagation"), wasm);
    }

    /// Names of the component methods a handler expression calls (`save()`, `this.save()`), for the wasm flag.
    public static Set<String> calledMethods(Expr handler) {
        final var names = new LinkedHashSet<String>();
        collect(handler, names);
        return names;
    }

    private static void collect(Expr expr, Set<String> names) {
        switch (expr) {
            case Expr.Call c -> {
                componentMethod(c.receiver()).ifPresent(names::add);
                c.args().forEach(a -> collect(a, names));
                collect(c.receiver(), names);
            }
            case Expr.SafeCall c -> {
                componentMethod(c.receiver()).ifPresent(names::add);
                c.args().forEach(a -> collect(a, names));
                collect(c.receiver(), names);
            }
            case Expr.Chain c -> c.expressions().forEach(e -> collect(e, names));
            case Expr.Conditional c -> {
                collect(c.condition(), names);
                collect(c.trueExp(), names);
                collect(c.falseExp(), names);
            }
            case Expr.Binary b -> {
                collect(b.left(), names);
                collect(b.right(), names);
            }
            case Expr.ParenthesizedExpression p -> collect(p.expression(), names);
            case Expr.PrefixNot p -> collect(p.expression(), names);
            default -> { }
        }
    }

    private static java.util.Optional<String> componentMethod(Expr receiver) {
        if (receiver instanceof Expr.PropertyRead read
                && (read.receiver() instanceof Expr.ImplicitReceiver || read.receiver() instanceof Expr.ThisReceiver)) {
            return java.util.Optional.of(read.name());
        }
        return java.util.Optional.empty();
    }

    private static boolean calls(Expr expr, String method) {
        return switch (expr) {
            case Expr.Call c -> isEventMethod(c.receiver(), method) || c.args().stream().anyMatch(a -> calls(a, method)) || calls(c.receiver(), method);
            case Expr.SafeCall c -> isEventMethod(c.receiver(), method) || calls(c.receiver(), method);
            case Expr.Chain c -> c.expressions().stream().anyMatch(e -> calls(e, method));
            case Expr.Conditional c -> calls(c.condition(), method) || calls(c.trueExp(), method) || calls(c.falseExp(), method);
            case Expr.Binary b -> calls(b.left(), method) || calls(b.right(), method);
            case Expr.ParenthesizedExpression p -> calls(p.expression(), method);
            case Expr.PrefixNot p -> calls(p.expression(), method);
            default -> false;
        };
    }

    private static boolean isEventMethod(Expr receiver, String method) {
        return receiver instanceof Expr.PropertyRead read && read.name().equals(method)
                && read.receiver() instanceof Expr.PropertyRead event && event.name().equals("$event")
                && event.receiver() instanceof Expr.ImplicitReceiver;
    }

    public String suffix() {
        final var flags = new java.util.ArrayList<String>();
        if (preventDefault) flags.add("pd");
        if (stopPropagation) flags.add("sp");
        if (wasm) flags.add("w");
        return flags.isEmpty() ? "" : ":" + String.join(",", flags);
    }
}
