package dev.isira.jaspring.template.parse;

import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.expr.JsUndefined;

/// Port of Angular's test unparser, so expectations copied from Angular's specs compare verbatim.
final class Unparser {

    private Unparser() {}

    static String unparse(Expr expr) {
        final var out = new StringBuilder();
        visit(expr, out);
        return out.toString();
    }

    private static void visit(Expr expr, StringBuilder out) {
        switch (expr) {
            case Expr.PropertyRead e -> {
                visit(e.receiver(), out);
                out.append(isImplicit(e.receiver()) ? e.name() : "." + e.name());
            }
            case Expr.SafePropertyRead e -> {
                visit(e.receiver(), out);
                out.append("?.").append(e.name());
            }
            case Expr.Unary e -> {
                out.append(e.operator());
                visit(e.expr(), out);
            }
            case Expr.Binary e -> {
                visit(e.left(), out);
                out.append(' ').append(e.operation().symbol()).append(' ');
                visit(e.right(), out);
            }
            case Expr.Chain e -> {
                for (var i = 0; i < e.expressions().size(); i++) {
                    visit(e.expressions().get(i), out);
                    out.append(i == e.expressions().size() - 1 ? ";" : "; ");
                }
            }
            case Expr.Conditional e -> {
                visit(e.condition(), out);
                out.append(" ? ");
                visit(e.trueExp(), out);
                out.append(" : ");
                visit(e.falseExp(), out);
            }
            case Expr.BindingPipe e -> {
                out.append('(');
                visit(e.exp(), out);
                out.append(" | ").append(e.name());
                for (final var arg : e.args()) {
                    out.append(':');
                    visit(arg, out);
                }
                out.append(')');
            }
            case Expr.Call e -> {
                visit(e.receiver(), out);
                out.append('(');
                args(e.args(), out);
                out.append(')');
            }
            case Expr.SafeCall e -> {
                visit(e.receiver(), out);
                out.append("?.(");
                args(e.args(), out);
                out.append(')');
            }
            case Expr.ImplicitReceiver e -> {}
            case Expr.ThisReceiver e -> {}
            case Expr.EmptyExpr e -> {}
            case Expr.Interpolation e -> {
                for (var i = 0; i < e.strings().size(); i++) {
                    out.append(e.strings().get(i));
                    if (i < e.expressions().size()) {
                        out.append("{{ ");
                        visit(e.expressions().get(i), out);
                        out.append(" }}");
                    }
                }
            }
            case Expr.KeyedRead e -> {
                visit(e.receiver(), out);
                out.append('[');
                visit(e.key(), out);
                out.append(']');
            }
            case Expr.SafeKeyedRead e -> {
                visit(e.receiver(), out);
                out.append("?.[");
                visit(e.key(), out);
                out.append(']');
            }
            case Expr.LiteralArray e -> {
                out.append('[');
                args(e.expressions(), out);
                out.append(']');
            }
            case Expr.LiteralMap e -> {
                out.append('{');
                for (var i = 0; i < e.keys().size(); i++) {
                    if (i > 0) out.append(", ");
                    switch (e.keys().get(i)) {
                        case Expr.MapKey.Spread s -> out.append("...");
                        case Expr.MapKey.Property p -> out.append(p.quoted() ? "\"" + p.key() + "\"" : p.key()).append(": ");
                    }
                    visit(e.values().get(i), out);
                }
                out.append('}');
            }
            case Expr.LiteralPrimitive e -> out.append(switch (e.value()) {
                case String s -> "\"" + s + "\"";
                case Double d -> jsNumber(d);
                case null -> "null";
                default -> e.value().toString();
            });
            case Expr.PrefixNot e -> {
                out.append('!');
                visit(e.expression(), out);
            }
            case Expr.TypeofExpression e -> {
                out.append("typeof ");
                visit(e.expression(), out);
            }
            case Expr.VoidExpression e -> {
                out.append("void ");
                visit(e.expression(), out);
            }
            case Expr.NonNullAssert e -> {
                visit(e.expression(), out);
                out.append('!');
            }
            case Expr.TemplateLiteral e -> {
                out.append('`');
                for (var i = 0; i < e.elements().size(); i++) {
                    out.append(e.elements().get(i));
                    if (i < e.expressions().size()) {
                        out.append("${");
                        visit(e.expressions().get(i), out);
                        out.append('}');
                    }
                }
                out.append('`');
            }
            case Expr.TaggedTemplateLiteral e -> {
                visit(e.tag(), out);
                visit(e.template(), out);
            }
            case Expr.ParenthesizedExpression e -> {
                out.append('(');
                visit(e.expression(), out);
                out.append(')');
            }
            case Expr.RegularExpressionLiteral e -> out.append('/').append(e.body()).append('/').append(e.flags() == null ? "" : e.flags());
            case Expr.SpreadElement e -> {
                out.append("...");
                visit(e.expression(), out);
            }
            case Expr.ArrowFunction e -> {
                out.append(e.parameters().size() == 1 ? e.parameters().get(0) : "(" + String.join(", ", e.parameters()) + ")");
                out.append(" => ");
                visit(e.body(), out);
            }
        }
    }

    private static void args(java.util.List<Expr> args, StringBuilder out) {
        for (var i = 0; i < args.size(); i++) {
            if (i > 0) out.append(", ");
            visit(args.get(i), out);
        }
    }

    private static boolean isImplicit(Expr receiver) {
        return receiver instanceof Expr.ImplicitReceiver || receiver instanceof Expr.ThisReceiver;
    }

    private static String jsNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) return Long.toString((long) d);
        return Double.toString(d);
    }

    static String valueText(Object value) {
        return value == JsUndefined.INSTANCE ? "undefined" : String.valueOf(value);
    }
}
