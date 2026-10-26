package dev.isira.jaspring.template.render;

import dev.isira.jaspring.template.expr.Expr;

/// Decides at render time what the browser must do when it dispatches an event, because the server
/// cannot answer synchronously: `preventDefault` when the handler ends in a literal `false` or calls
/// `$event.preventDefault()`, `stopPropagation` when it calls `$event.stopPropagation()`.
public record HandlerFlags(boolean preventDefault, boolean stopPropagation) {

    public static HandlerFlags analyze(Expr handler) {
        final var last = handler instanceof Expr.Chain chain && !chain.expressions().isEmpty()
                ? chain.expressions().getLast()
                : handler;
        final var returnsFalse = last instanceof Expr.LiteralPrimitive literal && Boolean.FALSE.equals(literal.value());
        return new HandlerFlags(returnsFalse || calls(handler, "preventDefault"), calls(handler, "stopPropagation"));
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
        if (preventDefault && stopPropagation) return ":pd,sp";
        if (preventDefault) return ":pd";
        if (stopPropagation) return ":sp";
        return "";
    }
}
