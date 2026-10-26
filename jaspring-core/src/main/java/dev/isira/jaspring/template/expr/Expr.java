package dev.isira.jaspring.template.expr;

import java.util.List;

/// Angular's expression AST (`expression_parser/ast.ts`) as Java records, one per class, same names and fields.
/// `ASTWithSource` wrappers are unwrapped by the converter.
public sealed interface Expr {

    ExprSpan span();

    record EmptyExpr(ExprSpan span) implements Expr {}

    record ImplicitReceiver(ExprSpan span) implements Expr {}

    record ThisReceiver(ExprSpan span) implements Expr {}

    record Chain(List<Expr> expressions, ExprSpan span) implements Expr {}

    record Conditional(Expr condition, Expr trueExp, Expr falseExp, ExprSpan span) implements Expr {}

    record PropertyRead(Expr receiver, String name, ExprSpan nameSpan, ExprSpan span) implements Expr {}

    record SafePropertyRead(Expr receiver, String name, ExprSpan nameSpan, ExprSpan span) implements Expr {}

    record KeyedRead(Expr receiver, Expr key, ExprSpan span) implements Expr {}

    record SafeKeyedRead(Expr receiver, Expr key, ExprSpan span) implements Expr {}

    record BindingPipe(Expr exp, String name, List<Expr> args, ExprSpan nameSpan, ExprSpan span) implements Expr {}

    /// `value` is a `String`, `Long` (integral number), `Double`, `Boolean`, `null`, or `JsUndefined`.
    record LiteralPrimitive(Object value, ExprSpan span) implements Expr {}

    record LiteralArray(List<Expr> expressions, ExprSpan span) implements Expr {}

    record SpreadElement(Expr expression, ExprSpan span) implements Expr {}

    record LiteralMap(List<MapKey> keys, List<Expr> values, ExprSpan span) implements Expr {}

    sealed interface MapKey {
        record Property(String key, boolean quoted) implements MapKey {}

        record Spread() implements MapKey {}
    }

    record Interpolation(List<String> strings, List<Expr> expressions, ExprSpan span) implements Expr {}

    record Binary(BinaryOp operation, Expr left, Expr right, ExprSpan span) implements Expr {}

    /// Unary plus or minus. Angular models it as a `Binary` subclass; here it is its own node.
    record Unary(char operator, Expr expr, ExprSpan span) implements Expr {}

    record PrefixNot(Expr expression, ExprSpan span) implements Expr {}

    record TypeofExpression(Expr expression, ExprSpan span) implements Expr {}

    record VoidExpression(Expr expression, ExprSpan span) implements Expr {}

    record NonNullAssert(Expr expression, ExprSpan span) implements Expr {}

    record Call(Expr receiver, List<Expr> args, ExprSpan argumentSpan, ExprSpan span) implements Expr {}

    record SafeCall(Expr receiver, List<Expr> args, ExprSpan argumentSpan, ExprSpan span) implements Expr {}

    /// `elements` has one more entry than `expressions`; text and expressions alternate.
    record TemplateLiteral(List<String> elements, List<Expr> expressions, ExprSpan span) implements Expr {}

    record TaggedTemplateLiteral(Expr tag, TemplateLiteral template, ExprSpan span) implements Expr {}

    record ParenthesizedExpression(Expr expression, ExprSpan span) implements Expr {}

    record ArrowFunction(List<String> parameters, Expr body, ExprSpan span) implements Expr {}

    record RegularExpressionLiteral(String body, String flags, ExprSpan span) implements Expr {}
}
