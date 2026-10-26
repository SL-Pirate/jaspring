package dev.isira.jaspring.template.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Value;

import dev.isira.jaspring.template.ast.BindingType;
import dev.isira.jaspring.template.ast.BlockSpans;
import dev.isira.jaspring.template.ast.DeferredTrigger;
import dev.isira.jaspring.template.ast.EventType;
import dev.isira.jaspring.template.ast.I18nNode;
import dev.isira.jaspring.template.ast.SecurityContext;
import dev.isira.jaspring.template.ast.SourceSpan;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.ast.TemplateNode.Attribute;
import dev.isira.jaspring.template.ast.TemplateNode.BoundAttribute;
import dev.isira.jaspring.template.ast.TemplateNode.BoundEvent;
import dev.isira.jaspring.template.ast.TemplateNode.BoundText;
import dev.isira.jaspring.template.ast.TemplateNode.Reference;
import dev.isira.jaspring.template.ast.TemplateNode.TextAttribute;
import dev.isira.jaspring.template.ast.TemplateNode.UnknownBlock;
import dev.isira.jaspring.template.ast.TemplateNode.Variable;
import dev.isira.jaspring.template.expr.BinaryOp;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.expr.ExprSpan;
import dev.isira.jaspring.template.expr.JsUndefined;

/// Walks Angular's AST objects (polyglot values) and builds the Java records. Dispatches on `constructor.name`,
/// which is why the JS bundle is built with `keepNames`.
final class NodeConverter {

    private final String url;

    NodeConverter(String url) {
        this.url = url;
    }

    List<TemplateNode> nodes(Value array) {
        return list(array, this::node);
    }

    TemplateNode node(Value v) {
        return switch (className(v)) {
            case "Text" -> new TemplateNode.Text(str(v, "value"), span(v, "sourceSpan"));
            case "BoundText" -> boundText(v);
            case "TextAttribute" -> textAttribute(v);
            case "BoundAttribute" -> boundAttribute(v);
            case "BoundEvent" -> boundEvent(v);
            case "Element" -> new TemplateNode.Element(str(v, "name"), list(v.getMember("attributes"), this::textAttribute),
                    list(v.getMember("inputs"), this::boundAttribute), list(v.getMember("outputs"), this::boundEvent),
                    nodes(v.getMember("children")), list(v.getMember("references"), this::reference),
                    bool(v, "isSelfClosing"), bool(v, "isVoid"), span(v, "sourceSpan"), span(v, "startSourceSpan"),
                    span(v, "endSourceSpan"));
            case "Template" -> new TemplateNode.Template(str(v, "tagName"), list(v.getMember("attributes"), this::textAttribute),
                    list(v.getMember("inputs"), this::boundAttribute), list(v.getMember("outputs"), this::boundEvent),
                    list(v.getMember("templateAttrs"), this::attribute), nodes(v.getMember("children")),
                    list(v.getMember("references"), this::reference), list(v.getMember("variables"), this::variable),
                    bool(v, "isSelfClosing"), span(v, "sourceSpan"), span(v, "startSourceSpan"), span(v, "endSourceSpan"));
            case "Content" -> new TemplateNode.Content(str(v, "selector"), list(v.getMember("attributes"), this::textAttribute),
                    nodes(v.getMember("children")), bool(v, "isSelfClosing"), span(v, "sourceSpan"),
                    span(v, "startSourceSpan"), span(v, "endSourceSpan"));
            case "Variable" -> variable(v);
            case "Reference" -> reference(v);
            case "LetDeclaration" -> new TemplateNode.LetDeclaration(str(v, "name"), expr(v.getMember("value")),
                    span(v, "sourceSpan"), span(v, "nameSpan"), span(v, "valueSpan"));
            case "Icu" -> icu(v);
            case "IfBlock" -> new TemplateNode.IfBlock(list(v.getMember("branches"), this::ifBranch), blockSpans(v));
            case "ForLoopBlock" -> new TemplateNode.ForLoopBlock(variable(v.getMember("item")), expr(v.getMember("expression")),
                    expr(v.getMember("trackBy")), list(v.getMember("contextVariables"), this::variable),
                    nodes(v.getMember("children")), optional(v.getMember("empty"), this::forEmpty),
                    span(v, "mainBlockSpan"), blockSpans(v));
            case "SwitchBlock" -> new TemplateNode.SwitchBlock(expr(v.getMember("expression")),
                    list(v.getMember("groups"), this::switchGroup), list(v.getMember("unknownBlocks"), this::unknownBlock),
                    optional(v.getMember("exhaustiveCheck"), e -> new TemplateNode.SwitchExhaustiveCheck(blockSpans(e))),
                    blockSpans(v));
            case "DeferredBlock" -> new TemplateNode.DeferredBlock(nodes(v.getMember("children")),
                    triggers(v.getMember("triggers")), triggers(v.getMember("prefetchTriggers")),
                    triggers(v.getMember("hydrateTriggers")),
                    optional(v.getMember("placeholder"), p -> new TemplateNode.DeferredBlockPlaceholder(
                            nodes(p.getMember("children")), intOrNull(p, "minimumTime"), blockSpans(p))),
                    optional(v.getMember("loading"), l -> new TemplateNode.DeferredBlockLoading(
                            nodes(l.getMember("children")), intOrNull(l, "afterTime"), intOrNull(l, "minimumTime"), blockSpans(l))),
                    optional(v.getMember("error"), e -> new TemplateNode.DeferredBlockError(nodes(e.getMember("children")), blockSpans(e))),
                    span(v, "mainBlockSpan"), blockSpans(v));
            case "UnknownBlock" -> unknownBlock(v);
            case "Component", "Directive", "HostElement" ->
                    throw new IllegalStateException("Selectorless syntax is not supported: " + className(v));
            default -> throw new IllegalStateException("Unknown template node " + className(v));
        };
    }

    private BoundText boundText(Value v) {
        return new BoundText(expr(v.getMember("value")), span(v, "sourceSpan"));
    }

    private TextAttribute textAttribute(Value v) {
        return new TextAttribute(str(v, "name"), str(v, "value"), span(v, "sourceSpan"), span(v, "keySpan"), span(v, "valueSpan"));
    }

    private BoundAttribute boundAttribute(Value v) {
        return new BoundAttribute(str(v, "name"), BindingType.fromOrdinal(v.getMember("type").asInt()),
                SecurityContext.fromOrdinal(v.getMember("securityContext").asInt()), expr(v.getMember("value")),
                str(v, "unit"), span(v, "sourceSpan"), span(v, "keySpan"), span(v, "valueSpan"));
    }

    private Attribute attribute(Value v) {
        return className(v).equals("TextAttribute") ? textAttribute(v) : boundAttribute(v);
    }

    private BoundEvent boundEvent(Value v) {
        return new BoundEvent(str(v, "name"), EventType.fromOrdinal(v.getMember("type").asInt()), expr(v.getMember("handler")),
                str(v, "target"), str(v, "phase"), span(v, "sourceSpan"), span(v, "handlerSpan"), span(v, "keySpan"));
    }

    private Variable variable(Value v) {
        return new Variable(str(v, "name"), str(v, "value"), span(v, "sourceSpan"), span(v, "keySpan"), span(v, "valueSpan"));
    }

    private Reference reference(Value v) {
        return new Reference(str(v, "name"), str(v, "value"), span(v, "sourceSpan"), span(v, "keySpan"), span(v, "valueSpan"));
    }

    private UnknownBlock unknownBlock(Value v) {
        return new UnknownBlock(str(v, "name"), span(v, "sourceSpan"), span(v, "nameSpan"));
    }

    private TemplateNode.IfBlockBranch ifBranch(Value v) {
        return new TemplateNode.IfBlockBranch(optional(v.getMember("expression"), this::expr), nodes(v.getMember("children")),
                optional(v.getMember("expressionAlias"), this::variable), blockSpans(v));
    }

    private TemplateNode.ForLoopBlockEmpty forEmpty(Value v) {
        return new TemplateNode.ForLoopBlockEmpty(nodes(v.getMember("children")), blockSpans(v));
    }

    private TemplateNode.SwitchBlockCaseGroup switchGroup(Value v) {
        return new TemplateNode.SwitchBlockCaseGroup(
                list(v.getMember("cases"), c -> new TemplateNode.SwitchBlockCase(optional(c.getMember("expression"), this::expr), blockSpans(c))),
                nodes(v.getMember("children")), blockSpans(v));
    }

    private List<DeferredTrigger> triggers(Value object) {
        final var result = new ArrayList<DeferredTrigger>();
        for (final var key : object.getMemberKeys()) {
            final var t = object.getMember(key);
            final var span = span(t, "sourceSpan");
            result.add(switch (key) {
                case "when" -> new DeferredTrigger.When(expr(t.getMember("value")), span);
                case "idle" -> new DeferredTrigger.Idle(span);
                case "immediate" -> new DeferredTrigger.Immediate(span);
                case "hover" -> new DeferredTrigger.Hover(str(t, "reference"), span);
                case "timer" -> new DeferredTrigger.Timer(t.getMember("delay").asInt(), span);
                case "interaction" -> new DeferredTrigger.Interaction(str(t, "reference"), span);
                case "viewport" -> new DeferredTrigger.Viewport(str(t, "reference"),
                        optional(t.getMember("options"), o -> (Expr.LiteralMap) expr(o)), span);
                case "never" -> new DeferredTrigger.Never(span);
                default -> throw new IllegalStateException("Unknown defer trigger " + key);
            });
        }
        return List.copyOf(result);
    }

    private TemplateNode.Icu icu(Value v) {
        final var vars = new LinkedHashMap<String, BoundText>();
        final var varsValue = v.getMember("vars");
        for (final var key : varsValue.getMemberKeys()) {
            vars.put(key, boundText(varsValue.getMember(key)));
        }
        final var placeholders = new LinkedHashMap<String, TemplateNode>();
        final var placeholdersValue = v.getMember("placeholders");
        for (final var key : placeholdersValue.getMemberKeys()) {
            placeholders.put(key, node(placeholdersValue.getMember(key)));
        }
        var meta = v.getMember("i18n");
        if (meta != null && !meta.isNull() && className(meta).equals("Message")) {
            meta = meta.getMember("nodes").getArrayElement(0);
        }
        final var message = meta == null || meta.isNull() ? null : (I18nNode.Icu) i18n(meta);
        return new TemplateNode.Icu(ordered(vars), ordered(placeholders), message, span(v, "sourceSpan"));
    }

    private I18nNode i18n(Value v) {
        return switch (className(v)) {
            case "Text" -> new I18nNode.Text(str(v, "value"));
            case "Container" -> new I18nNode.Container(list(v.getMember("children"), this::i18n));
            case "Icu" -> {
                final var cases = new LinkedHashMap<String, I18nNode>();
                final var casesValue = v.getMember("cases");
                for (final var key : casesValue.getMemberKeys()) {
                    cases.put(key, i18n(casesValue.getMember(key)));
                }
                yield new I18nNode.Icu(str(v, "expression"), str(v, "type"), ordered(cases), str(v, "expressionPlaceholder"));
            }
            case "Placeholder" -> new I18nNode.Placeholder(str(v, "value"), str(v, "name"));
            case "IcuPlaceholder" -> new I18nNode.IcuPlaceholder((I18nNode.Icu) i18n(v.getMember("value")), str(v, "name"));
            case "TagPlaceholder" -> {
                final var attrs = new LinkedHashMap<String, String>();
                final var attrsValue = v.getMember("attrs");
                for (final var key : attrsValue.getMemberKeys()) {
                    attrs.put(key, attrsValue.getMember(key).asString());
                }
                yield new I18nNode.TagPlaceholder(str(v, "tag"), ordered(attrs), str(v, "startName"), str(v, "closeName"),
                        list(v.getMember("children"), this::i18n), bool(v, "isVoid"));
            }
            case "BlockPlaceholder" -> new I18nNode.BlockPlaceholder(str(v, "name"), list(v.getMember("parameters"), Value::asString),
                    str(v, "startName"), str(v, "closeName"), list(v.getMember("children"), this::i18n));
            default -> throw new IllegalStateException("Unknown i18n node " + className(v));
        };
    }

    // ---------------- expressions ----------------

    Expr expr(Value v) {
        while (className(v).equals("ASTWithSource")) {
            v = v.getMember("ast");
        }
        final var span = exprSpan(v);
        return switch (className(v)) {
            case "EmptyExpr" -> new Expr.EmptyExpr(span);
            case "ImplicitReceiver" -> new Expr.ImplicitReceiver(span);
            case "ThisReceiver" -> new Expr.ThisReceiver(span);
            case "Chain" -> new Expr.Chain(exprs(v.getMember("expressions")), span);
            case "Conditional" -> new Expr.Conditional(expr(v.getMember("condition")), expr(v.getMember("trueExp")),
                    expr(v.getMember("falseExp")), span);
            case "PropertyRead" -> new Expr.PropertyRead(expr(v.getMember("receiver")), str(v, "name"), absoluteSpan(v.getMember("nameSpan")), span);
            case "SafePropertyRead" -> new Expr.SafePropertyRead(expr(v.getMember("receiver")), str(v, "name"), absoluteSpan(v.getMember("nameSpan")), span);
            case "KeyedRead" -> new Expr.KeyedRead(expr(v.getMember("receiver")), expr(v.getMember("key")), span);
            case "SafeKeyedRead" -> new Expr.SafeKeyedRead(expr(v.getMember("receiver")), expr(v.getMember("key")), span);
            case "BindingPipe" -> new Expr.BindingPipe(expr(v.getMember("exp")), str(v, "name"), exprs(v.getMember("args")),
                    absoluteSpan(v.getMember("nameSpan")), span);
            case "LiteralPrimitive" -> new Expr.LiteralPrimitive(primitive(v.getMember("value")), span);
            case "LiteralArray" -> new Expr.LiteralArray(exprs(v.getMember("expressions")), span);
            case "SpreadElement" -> new Expr.SpreadElement(expr(v.getMember("expression")), span);
            case "LiteralMap" -> new Expr.LiteralMap(list(v.getMember("keys"), this::mapKey), exprs(v.getMember("values")), span);
            case "Interpolation" -> new Expr.Interpolation(list(v.getMember("strings"), Value::asString), exprs(v.getMember("expressions")), span);
            case "Unary" -> new Expr.Unary(str(v, "operator").charAt(0), expr(v.getMember("expr")), span);
            case "Binary" -> new Expr.Binary(BinaryOp.fromSymbol(str(v, "operation")), expr(v.getMember("left")), expr(v.getMember("right")), span);
            case "PrefixNot" -> new Expr.PrefixNot(expr(v.getMember("expression")), span);
            case "TypeofExpression" -> new Expr.TypeofExpression(expr(v.getMember("expression")), span);
            case "VoidExpression" -> new Expr.VoidExpression(expr(v.getMember("expression")), span);
            case "NonNullAssert" -> new Expr.NonNullAssert(expr(v.getMember("expression")), span);
            case "Call" -> new Expr.Call(expr(v.getMember("receiver")), exprs(v.getMember("args")), absoluteSpan(v.getMember("argumentSpan")), span);
            case "SafeCall" -> new Expr.SafeCall(expr(v.getMember("receiver")), exprs(v.getMember("args")), absoluteSpan(v.getMember("argumentSpan")), span);
            case "TemplateLiteral" -> templateLiteral(v, span);
            case "TaggedTemplateLiteral" -> new Expr.TaggedTemplateLiteral(expr(v.getMember("tag")),
                    templateLiteral(v.getMember("template"), exprSpan(v.getMember("template"))), span);
            case "ParenthesizedExpression" -> new Expr.ParenthesizedExpression(expr(v.getMember("expression")), span);
            case "ArrowFunction" -> new Expr.ArrowFunction(list(v.getMember("parameters"), p -> p.getMember("name").asString()),
                    expr(v.getMember("body")), span);
            case "RegularExpressionLiteral" -> new Expr.RegularExpressionLiteral(str(v, "body"), str(v, "flags"), span);
            default -> throw new IllegalStateException("Unknown expression node " + className(v));
        };
    }

    private Expr.TemplateLiteral templateLiteral(Value v, ExprSpan span) {
        return new Expr.TemplateLiteral(list(v.getMember("elements"), e -> e.getMember("text").asString()),
                exprs(v.getMember("expressions")), span);
    }

    private Expr.MapKey mapKey(Value v) {
        if (str(v, "kind").equals("spread")) return new Expr.MapKey.Spread();
        return new Expr.MapKey.Property(str(v, "key"), bool(v, "quoted"));
    }

    private List<Expr> exprs(Value array) {
        return list(array, this::expr);
    }

    private static Object primitive(Value v) {
        if (v == null || v.isNull()) {
            // GraalJS reports both null and undefined as isNull; the JS side keeps them apart through toString.
            return v != null && "undefined".equals(v.toString()) ? JsUndefined.INSTANCE : null;
        }
        if (v.isString()) return v.asString();
        if (v.isBoolean()) return v.asBoolean();
        if (v.fitsInLong() && !hasFraction(v)) return v.asLong();
        return v.asDouble();
    }

    private static boolean hasFraction(Value v) {
        final var d = v.asDouble();
        return d != Math.rint(d) || Double.isInfinite(d) || (d == 0 && 1 / d < 0);
    }

    // ---------------- helpers ----------------

    // Map.copyOf would lose insertion order; ICU cases must keep their source order.
    private static <V> Map<String, V> ordered(LinkedHashMap<String, V> map) {
        return Collections.unmodifiableMap(map);
    }

    static String className(Value v) {
        return v.getMember("constructor").getMember("name").asString();
    }

    private static String str(Value v, String member) {
        final var m = v.getMember(member);
        return m == null || m.isNull() ? null : m.asString();
    }

    private static boolean bool(Value v, String member) {
        final var m = v.getMember(member);
        return m != null && !m.isNull() && m.asBoolean();
    }

    private static Integer intOrNull(Value v, String member) {
        final var m = v.getMember(member);
        return m == null || m.isNull() ? null : m.asInt();
    }

    private static <T> T optional(Value v, Function<Value, T> convert) {
        return v == null || v.isNull() ? null : convert.apply(v);
    }

    private static <T> List<T> list(Value array, Function<Value, T> convert) {
        if (array == null || array.isNull()) return List.of();
        final var result = new ArrayList<T>((int) array.getArraySize());
        for (var i = 0L; i < array.getArraySize(); i++) {
            result.add(convert.apply(array.getArrayElement(i)));
        }
        return List.copyOf(result);
    }

    private SourceSpan span(Value v, String member) {
        final var s = v.getMember(member);
        if (s == null || s.isNull()) return null;
        final var start = s.getMember("start");
        final var end = s.getMember("end");
        return new SourceSpan(url, start.getMember("offset").asInt(), end.getMember("offset").asInt(),
                start.getMember("line").asInt(), start.getMember("col").asInt());
    }

    private BlockSpans blockSpans(Value v) {
        return new BlockSpans(span(v, "nameSpan"), span(v, "sourceSpan"), span(v, "startSourceSpan"), span(v, "endSourceSpan"));
    }

    private static ExprSpan exprSpan(Value v) {
        final var span = v.getMember("span");
        final var source = v.getMember("sourceSpan");
        return new ExprSpan(span.getMember("start").asInt(), span.getMember("end").asInt(),
                source.getMember("start").asInt(), source.getMember("end").asInt());
    }

    private static ExprSpan absoluteSpan(Value v) {
        if (v == null || v.isNull()) return ExprSpan.NONE;
        final var start = v.getMember("start").asInt();
        final var end = v.getMember("end").asInt();
        return new ExprSpan(start, end, start, end);
    }
}
