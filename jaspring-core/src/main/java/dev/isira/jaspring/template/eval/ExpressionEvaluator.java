package dev.isira.jaspring.template.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import dev.isira.jaspring.signals.Signal;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.template.expr.BinaryOp;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.expr.JsUndefined;

/// Interprets Angular expression ASTs against Java objects with JavaScript semantics.
///
/// Safe navigation (`a?.b.c`) short-circuits the whole access chain to `null`, like Angular's generated code.
/// Assignments are only produced by the parser inside event handlers.
public final class ExpressionEvaluator {

    /// Marks a value produced by a short-circuited safe access; consumers outside the chain see `null`.
    private static final Object SHORT_CIRCUIT = new Object();

    private ExpressionEvaluator() {}

    public static Object evaluate(Expr expr, EvaluationContext context) {
        return value(expr, context);
    }

    private static Object value(Expr expr, EvaluationContext ctx) {
        final var result = eval(expr, ctx);
        return result == SHORT_CIRCUIT ? null : result;
    }

    private static Object eval(Expr expr, EvaluationContext ctx) {
        return switch (expr) {
            case Expr.EmptyExpr e -> JsUndefined.INSTANCE;
            case Expr.ImplicitReceiver e -> ctx.component();
            case Expr.ThisReceiver e -> ctx.component();
            case Expr.LiteralPrimitive e -> e.value();
            case Expr.ParenthesizedExpression e -> eval(e.expression(), ctx);
            case Expr.NonNullAssert e -> eval(e.expression(), ctx);
            case Expr.PropertyRead e -> propertyRead(e.receiver(), e.name(), ctx);
            case Expr.SafePropertyRead e -> {
                final var receiver = eval(e.receiver(), ctx);
                yield receiver == SHORT_CIRCUIT || Js.isNullish(receiver) ? SHORT_CIRCUIT : MemberAccessor.read(receiver, e.name());
            }
            case Expr.KeyedRead e -> {
                final var receiver = eval(e.receiver(), ctx);
                yield receiver == SHORT_CIRCUIT ? SHORT_CIRCUIT : MemberAccessor.readIndex(receiver, value(e.key(), ctx));
            }
            case Expr.SafeKeyedRead e -> {
                final var receiver = eval(e.receiver(), ctx);
                yield receiver == SHORT_CIRCUIT || Js.isNullish(receiver) ? SHORT_CIRCUIT : MemberAccessor.readIndex(receiver, value(e.key(), ctx));
            }
            case Expr.Call e -> {
                final var callee = callee(e.receiver(), ctx);
                yield callee == SHORT_CIRCUIT ? SHORT_CIRCUIT : call(callee, e.receiver(), e.args(), ctx);
            }
            case Expr.SafeCall e -> {
                final var callee = callee(e.receiver(), ctx);
                yield callee == SHORT_CIRCUIT || Js.isNullish(callee) ? SHORT_CIRCUIT : call(callee, e.receiver(), e.args(), ctx);
            }
            case Expr.Chain e -> {
                Object last = JsUndefined.INSTANCE;
                for (final var expression : e.expressions()) {
                    last = value(expression, ctx);
                }
                yield last;
            }
            case Expr.Conditional e -> Js.truthy(value(e.condition(), ctx)) ? value(e.trueExp(), ctx) : value(e.falseExp(), ctx);
            case Expr.PrefixNot e -> !Js.truthy(value(e.expression(), ctx));
            case Expr.TypeofExpression e -> Js.typeOf(value(e.expression(), ctx));
            case Expr.VoidExpression e -> {
                value(e.expression(), ctx);
                yield JsUndefined.INSTANCE;
            }
            case Expr.Unary e -> unary(e, ctx);
            case Expr.Binary e -> e.operation().isAssignment() ? assign(e, ctx) : binary(e, ctx);
            case Expr.Interpolation e -> interpolate(e, ctx);
            case Expr.BindingPipe e -> pipe(e, ctx);
            case Expr.LiteralArray e -> {
                final var list = new ArrayList<>();
                for (final var element : e.expressions()) {
                    if (element instanceof Expr.SpreadElement spread) {
                        spreadInto(list, value(spread.expression(), ctx));
                    } else {
                        list.add(value(element, ctx));
                    }
                }
                yield list;
            }
            case Expr.SpreadElement e -> throw new EvaluationException("Spread is only valid inside array or object literals");
            case Expr.LiteralMap e -> literalMap(e, ctx);
            case Expr.TemplateLiteral e -> templateLiteral(e, ctx);
            case Expr.TaggedTemplateLiteral e -> {
                final var tag = value(e.tag(), ctx);
                final var args = new ArrayList<>();
                args.add(List.copyOf(e.template().elements()));
                for (final var expression : e.template().expressions()) {
                    args.add(value(expression, ctx));
                }
                yield invoke(tag, "tag", args.toArray());
            }
            case Expr.ArrowFunction e -> arrowFunction(e, ctx);
            case Expr.RegularExpressionLiteral e -> JsRegExp.of(e.body(), e.flags());
        };
    }

    private static Object propertyRead(Expr receiverExpr, String name, EvaluationContext ctx) {
        if (receiverExpr instanceof Expr.ImplicitReceiver) {
            final var local = ctx.scope().find(name);
            if (local != Scope.MISSING) return local;
            return MemberAccessor.read(ctx.component(), name);
        }
        final var receiver = eval(receiverExpr, ctx);
        return receiver == SHORT_CIRCUIT ? SHORT_CIRCUIT : MemberAccessor.read(receiver, name);
    }

    // The callee of `x.y(...)` resolves `y` as a method first; `(click)="reset()"` must not read a property named reset.
    // The receiver's declared type travels along so generic parameters (`WritableSignal<Integer>.set(T)`) coerce correctly.
    private static Object callee(Expr receiverExpr, EvaluationContext ctx) {
        return switch (receiverExpr) {
            case Expr.PropertyRead p when p.receiver() instanceof Expr.ImplicitReceiver -> {
                final var local = ctx.scope().find(p.name());
                yield local != Scope.MISSING ? local : MemberAccessor.readForCall(ctx.component(), p.name(), null);
            }
            case Expr.PropertyRead p -> {
                final var receiver = evalTyped(p.receiver(), ctx);
                yield receiver.value() == SHORT_CIRCUIT ? SHORT_CIRCUIT : MemberAccessor.readForCall(receiver.value(), p.name(), receiver.type());
            }
            case Expr.SafePropertyRead p -> {
                final var receiver = evalTyped(p.receiver(), ctx);
                yield receiver.value() == SHORT_CIRCUIT || Js.isNullish(receiver.value())
                        ? SHORT_CIRCUIT
                        : MemberAccessor.readForCall(receiver.value(), p.name(), receiver.type());
            }
            default -> eval(receiverExpr, ctx);
        };
    }

    private record Typed(Object value, java.lang.reflect.Type type) {}

    private static Typed evalTyped(Expr expr, EvaluationContext ctx) {
        return switch (expr) {
            case Expr.PropertyRead p when p.receiver() instanceof Expr.ImplicitReceiver -> {
                final var local = ctx.scope().find(p.name());
                if (local != Scope.MISSING) yield new Typed(local, null);
                yield new Typed(MemberAccessor.read(ctx.component(), p.name()), MemberAccessor.declaredType(ctx.component(), p.name()));
            }
            case Expr.PropertyRead p -> {
                final var receiver = evalTyped(p.receiver(), ctx);
                if (receiver.value() == SHORT_CIRCUIT) yield receiver;
                yield new Typed(MemberAccessor.read(receiver.value(), p.name()), MemberAccessor.declaredType(receiver.value(), p.name()));
            }
            case Expr.SafePropertyRead p -> {
                final var receiver = evalTyped(p.receiver(), ctx);
                if (receiver.value() == SHORT_CIRCUIT || Js.isNullish(receiver.value())) yield new Typed(SHORT_CIRCUIT, null);
                yield new Typed(MemberAccessor.read(receiver.value(), p.name()), MemberAccessor.declaredType(receiver.value(), p.name()));
            }
            case Expr.ParenthesizedExpression p -> evalTyped(p.expression(), ctx);
            case Expr.NonNullAssert n -> evalTyped(n.expression(), ctx);
            default -> new Typed(eval(expr, ctx), null);
        };
    }

    private static Object call(Object callee, Expr receiverExpr, List<Expr> argExprs, EvaluationContext ctx) {
        final var args = new Object[argExprs.size()];
        for (var i = 0; i < args.length; i++) {
            args[i] = value(argExprs.get(i), ctx);
        }
        return invoke(callee, describe(receiverExpr), args);
    }

    private static Object invoke(Object callee, String description, Object[] args) {
        return switch (callee) {
            case JsFunction fn -> fn.call(args);
            case Signal<?> signal when args.length == 0 -> signal.get();
            case Signal<?> signal -> throw new EvaluationException("Signals take no arguments: " + description + "()");
            case null, default -> throw new EvaluationException(description + " is not a function");
        };
    }

    private static String describe(Expr expr) {
        return switch (expr) {
            case Expr.PropertyRead e -> e.receiver() instanceof Expr.ImplicitReceiver ? e.name() : describe(e.receiver()) + "." + e.name();
            case Expr.SafePropertyRead e -> describe(e.receiver()) + "?." + e.name();
            case Expr.ImplicitReceiver e -> "this";
            default -> expr.getClass().getSimpleName();
        };
    }

    private static Object unary(Expr.Unary e, EvaluationContext ctx) {
        final var operand = Js.toNumber(value(e.expr(), ctx));
        if (e.operator() == '+') return operand;
        return Js.isIntegral(operand) ? (Object) (-operand.longValue()) : (Object) (-operand.doubleValue());
    }

    private static Object binary(Expr.Binary e, EvaluationContext ctx) {
        return switch (e.operation()) {
            case AND -> {
                final var left = value(e.left(), ctx);
                yield Js.truthy(left) ? value(e.right(), ctx) : left;
            }
            case OR -> {
                final var left = value(e.left(), ctx);
                yield Js.truthy(left) ? left : value(e.right(), ctx);
            }
            case NULLISH -> {
                final var left = value(e.left(), ctx);
                yield Js.isNullish(left) ? value(e.right(), ctx) : left;
            }
            case INSTANCEOF -> throw new EvaluationException("'instanceof' is not supported in templates");
            default -> arithmetic(e.operation(), value(e.left(), ctx), value(e.right(), ctx));
        };
    }

    static Object arithmetic(BinaryOp op, Object left, Object right) {
        return switch (op) {
            case EQ -> Js.looseEquals(left, right);
            case NEQ -> !Js.looseEquals(left, right);
            case STRICT_EQ -> Js.strictEquals(left, right);
            case STRICT_NEQ -> !Js.strictEquals(left, right);
            case LT -> relational(left, right, c -> c < 0);
            case GT -> relational(left, right, c -> c > 0);
            case LTE -> relational(left, right, c -> c <= 0);
            case GTE -> relational(left, right, c -> c >= 0);
            case IN -> contains(right, left);
            case ADD -> Js.isStringLike(left) || Js.isStringLike(right)
                    ? Js.toJsString(left) + Js.toJsString(right)
                    : numeric(left, right, Math::addExact, Double::sum);
            case SUB -> numeric(left, right, Math::subtractExact, (a, b) -> a - b);
            case MUL -> numeric(left, right, Math::multiplyExact, (a, b) -> a * b);
            case DIV -> Js.normalize(Js.toDouble(left) / Js.toDouble(right));
            case MOD -> numeric(left, right, (a, b) -> b == 0 ? null : a % b, (a, b) -> a % b);
            case EXP -> Js.normalize(Math.pow(Js.toDouble(left), Js.toDouble(right)));
            default -> throw new EvaluationException("Unsupported operator " + op.symbol());
        };
    }

    private static boolean relational(Object left, Object right, java.util.function.IntPredicate test) {
        final var comparison = Js.compare(left, right);
        return comparison != Integer.MIN_VALUE && test.test(comparison);
    }

    private static Object numeric(Object left, Object right, BiFunction<Long, Long, Long> longs, BiFunction<Double, Double, Double> doubles) {
        final var a = Js.toNumber(left);
        final var b = Js.toNumber(right);
        if (Js.isIntegral(a) && Js.isIntegral(b)) {
            try {
                final var result = longs.apply(a.longValue(), b.longValue());
                if (result != null) return result;
            } catch (ArithmeticException overflow) {
                // fall through to double arithmetic, which is what JavaScript does anyway
            }
        }
        return Js.normalize(doubles.apply(a.doubleValue(), b.doubleValue()));
    }

    private static boolean contains(Object container, Object key) {
        return switch (container) {
            case Map<?, ?> map -> map.containsKey(key) || map.containsKey(Js.toJsString(key));
            case List<?> list -> {
                final var index = Js.toDouble(key);
                yield index >= 0 && index < list.size();
            }
            case null -> throw new EvaluationException("Cannot use 'in' operator to search for '" + Js.toJsString(key) + "' in null");
            default -> MemberAccessor.has(container, Js.toJsString(key));
        };
    }

    private static Object interpolate(Expr.Interpolation e, EvaluationContext ctx) {
        final var out = new StringBuilder();
        for (var i = 0; i < e.strings().size(); i++) {
            out.append(e.strings().get(i));
            if (i < e.expressions().size()) {
                out.append(Js.renderStringify(value(e.expressions().get(i), ctx)));
            }
        }
        return out.toString();
    }

    private static Object pipe(Expr.BindingPipe e, EvaluationContext ctx) {
        final var pipe = ctx.pipes().get(e.name());
        if (pipe == null) {
            throw new EvaluationException("The pipe '" + e.name() + "' could not be found");
        }
        final var args = new Object[e.args().size()];
        for (var i = 0; i < args.length; i++) {
            args[i] = value(e.args().get(i), ctx);
        }
        return pipe.transform(value(e.exp(), ctx), args);
    }

    private static Object literalMap(Expr.LiteralMap e, EvaluationContext ctx) {
        final var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < e.keys().size(); i++) {
            final var value = value(e.values().get(i), ctx);
            switch (e.keys().get(i)) {
                case Expr.MapKey.Property p -> map.put(p.key(), value);
                case Expr.MapKey.Spread s -> {
                    if (value instanceof Map<?, ?> source) {
                        source.forEach((k, v) -> map.put(Js.toJsString(k), v));
                    } else if (!Js.isNullish(value)) {
                        throw new EvaluationException("Cannot spread a " + value.getClass().getSimpleName() + " into an object literal");
                    }
                }
            }
        }
        return map;
    }

    private static void spreadInto(List<Object> target, Object source) {
        switch (source) {
            case Iterable<?> iterable -> iterable.forEach(target::add);
            case CharSequence s -> s.chars().forEach(c -> target.add(String.valueOf((char) c)));
            case null -> throw new EvaluationException("Cannot spread null into an array literal");
            default -> {
                if (source.getClass().isArray()) target.addAll(JsShim.arrayAsList(source));
                else throw new EvaluationException("Cannot spread a " + source.getClass().getSimpleName() + " into an array literal");
            }
        }
    }

    private static Object templateLiteral(Expr.TemplateLiteral e, EvaluationContext ctx) {
        final var out = new StringBuilder();
        for (var i = 0; i < e.elements().size(); i++) {
            out.append(e.elements().get(i));
            if (i < e.expressions().size()) {
                out.append(Js.toJsString(value(e.expressions().get(i), ctx)));
            }
        }
        return out.toString();
    }

    private static JsFunction arrowFunction(Expr.ArrowFunction e, EvaluationContext ctx) {
        return args -> {
            final var frame = new LinkedHashMap<String, Object>();
            for (var i = 0; i < e.parameters().size(); i++) {
                frame.put(e.parameters().get(i), i < args.length ? args[i] : JsUndefined.INSTANCE);
            }
            return value(e.body(), ctx.withScope(ctx.scope().child(frame)));
        };
    }

    // ---------------- assignments (event handlers only) ----------------

    private static Object assign(Expr.Binary e, EvaluationContext ctx) {
        final var target = assignmentTarget(e.left());
        final var op = e.operation();
        if (op == BinaryOp.ASSIGN) {
            final var newValue = value(e.right(), ctx);
            store(target, newValue, ctx);
            return newValue;
        }
        final var current = value(target, ctx);
        final Object newValue = switch (op) {
            case AND_ASSIGN -> Js.truthy(current) ? value(e.right(), ctx) : current;
            case OR_ASSIGN -> Js.truthy(current) ? current : value(e.right(), ctx);
            case NULLISH_ASSIGN -> Js.isNullish(current) ? value(e.right(), ctx) : current;
            default -> arithmetic(BinaryOp.fromSymbol(op.symbol().substring(0, op.symbol().length() - 1)), current, value(e.right(), ctx));
        };
        if (newValue != current) {
            store(target, newValue, ctx);
        }
        return newValue;
    }

    private static Expr assignmentTarget(Expr expr) {
        return switch (expr) {
            case Expr.ParenthesizedExpression p -> assignmentTarget(p.expression());
            case Expr.NonNullAssert n -> assignmentTarget(n.expression());
            case Expr.PropertyRead p -> p;
            case Expr.SafePropertyRead p -> p;
            case Expr.KeyedRead k -> k;
            default -> throw new EvaluationException("Cannot assign to " + expr.getClass().getSimpleName());
        };
    }

    private static void store(Expr target, Object newValue, EvaluationContext ctx) {
        switch (target) {
            case Expr.PropertyRead p -> storeProperty(p.receiver(), p.name(), newValue, ctx);
            case Expr.SafePropertyRead p -> {
                final var receiver = value(p.receiver(), ctx);
                if (!Js.isNullish(receiver)) assignMember(receiver, p.name(), newValue);
            }
            case Expr.KeyedRead k -> MemberAccessor.writeIndex(value(k.receiver(), ctx), value(k.key(), ctx), newValue);
            default -> throw new IllegalStateException();
        }
    }

    private static void storeProperty(Expr receiverExpr, String name, Object newValue, EvaluationContext ctx) {
        if (receiverExpr instanceof Expr.ImplicitReceiver) {
            if (ctx.scope().has(name)) {
                throw new EvaluationException("Cannot assign value to template variable '" + name + "'");
            }
            assignMember(ctx.component(), name, newValue);
            return;
        }
        assignMember(value(receiverExpr, ctx), name, newValue);
    }

    // Assigning to a member that holds a writable signal writes through it, which is what `[(ngModel)]="sig"` relies on.
    @SuppressWarnings("unchecked")
    private static void assignMember(Object target, String name, Object newValue) {
        if (MemberAccessor.has(target, name) && MemberAccessor.read(target, name) instanceof WritableSignal<?> signal) {
            final var valueType = GenericTypes.firstTypeArgument(MemberAccessor.declaredType(target, name));
            ((WritableSignal<Object>) signal).set(valueType == null ? newValue : ArgumentCoercion.coerce(newValue, valueType));
            return;
        }
        MemberAccessor.write(target, name, newValue);
    }
}
