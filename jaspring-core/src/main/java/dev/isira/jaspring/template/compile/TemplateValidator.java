package dev.isira.jaspring.template.compile;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.component.ComponentDef;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.component.Schema;
import dev.isira.jaspring.template.ast.BindingType;
import dev.isira.jaspring.template.ast.SourceSpan;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.MemberAccessor;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.expr.ExprSpan;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.TemplateError;

/// Walks a template once and reports, with Angular's wording, what cannot work at runtime.
final class TemplateValidator {

    private static final Set<String> DIRECTIVE_INPUTS = Set.of(
            "ngClass", "ngStyle", "ngSwitch", "ngTemplateOutlet", "ngTemplateOutletContext", "ngTemplateOutletInjector",
            "ngModel", "ngModelOptions", "ngNonBindable", "ngIf", "ngIfElse", "ngIfThen", "ngForOf", "ngForTrackBy",
            "ngSwitchCase", "ngSwitchDefault", "ngProjectAs");

    private static final Set<String> KNOWN_GLOBALS = Set.of("$any", "$event");

    private final ComponentDef def;
    private final ComponentRegistry registry;
    private final DomSchema schema;
    private final String source;
    private final String url;
    private final List<TemplateError> errors;

    TemplateValidator(ComponentDef def, ComponentRegistry registry, DomSchema schema, String source, String url, List<TemplateError> errors) {
        this.def = def;
        this.registry = registry;
        this.schema = schema;
        this.source = source;
        this.url = url;
        this.errors = errors;
    }

    void validate(List<TemplateNode> nodes) {
        visitView(nodes, new HashSet<>());
    }

    /// `locals` holds every name visible in this embedded view (parents included); references are hoisted per view.
    private void visitView(List<TemplateNode> nodes, Set<String> locals) {
        final var scope = new HashSet<>(locals);
        hoistReferences(nodes, scope);
        for (final var node : nodes) {
            visit(node, scope);
        }
    }

    private void hoistReferences(List<TemplateNode> nodes, Set<String> scope) {
        for (final var node : nodes) {
            switch (node) {
                case TemplateNode.Element e -> {
                    e.references().forEach(r -> scope.add(r.name()));
                    hoistReferences(e.children(), scope);
                }
                case TemplateNode.Template t -> t.references().forEach(r -> scope.add(r.name()));
                case TemplateNode.LetDeclaration let -> scope.add(let.name());
                default -> { }
            }
        }
    }

    private void visit(TemplateNode node, Set<String> scope) {
        switch (node) {
            case TemplateNode.Text t -> { }
            case TemplateNode.BoundText t -> expr(t.value(), scope);
            case TemplateNode.LetDeclaration let -> expr(let.value(), scope);
            case TemplateNode.Element e -> element(e, scope);
            case TemplateNode.Template t -> template(t, scope);
            case TemplateNode.Content c -> c.children().forEach(child -> visit(child, scope));
            case TemplateNode.IfBlock b -> {
                for (final var branch : b.branches()) {
                    if (branch.expression() != null) expr(branch.expression(), scope);
                    final var inner = new HashSet<>(scope);
                    if (branch.expressionAlias() != null) inner.add(branch.expressionAlias().name());
                    visitView(branch.children(), inner);
                }
            }
            case TemplateNode.ForLoopBlock b -> {
                expr(b.expression(), scope);
                final var inner = new HashSet<>(scope);
                inner.add(b.item().name());
                b.contextVariables().forEach(v -> inner.add(v.name()));
                expr(b.trackBy(), inner);
                visitView(b.children(), inner);
                if (b.empty() != null) visitView(b.empty().children(), scope);
            }
            case TemplateNode.SwitchBlock b -> {
                expr(b.expression(), scope);
                for (final var group : b.groups()) {
                    group.cases().forEach(c -> {
                        if (c.expression() != null) expr(c.expression(), scope);
                    });
                    visitView(group.children(), scope);
                }
            }
            case TemplateNode.DeferredBlock b -> {
                visitView(b.children(), scope);
                if (b.placeholder() != null) visitView(b.placeholder().children(), scope);
                if (b.loading() != null) visitView(b.loading().children(), scope);
                if (b.error() != null) visitView(b.error().children(), scope);
            }
            case TemplateNode.Icu icu -> {
                icu.vars().values().forEach(v -> expr(v.value(), scope));
                icu.placeholders().values().forEach(p -> visit(p, scope));
            }
            case TemplateNode.UnknownBlock b -> error("Unrecognized block @" + b.name(), b.sourceSpan());
            default -> { }
        }
    }

    private void element(TemplateNode.Element element, Set<String> scope) {
        // Selectors and messages use the local name; the schema is keyed by Angular's namespaced name (`:svg:circle`).
        final var tagName = localName(element.name());
        final var schemaName = element.name();
        final var attributes = new LinkedHashMap<String, String>();
        element.attributes().forEach(a -> attributes.put(localName(a.name()), a.value()));
        final var boundNames = new HashSet<String>();
        element.inputs().forEach(i -> boundNames.add(i.name()));
        final var structural = tagName.equals("ng-container") || tagName.equals("ng-content");
        final var component = structural ? null : registry.match(tagName, attributes, boundNames);

        if (structural) {
            // Angular's own elements: nothing to validate on the tag itself.
        } else if (component == null && !schema.hasElement(schemaName) && !def.schemas().contains(Schema.NO_ERRORS)) {
            error("'" + tagName + "' is not a known element:\n1. If '" + tagName + "' is a Jaspring component, then verify that it is annotated with @Component and scanned.\n2. To allow any element add 'NO_ERRORS_SCHEMA' to the '@Component.schemas' of this component.",
                    element.startSourceSpan());
        } else if (component == null && tagName.contains("-") && !schema.knownElements().contains(tagName)
                && !def.schemas().contains(Schema.CUSTOM_ELEMENTS) && !def.schemas().contains(Schema.NO_ERRORS)) {
            error("'" + tagName + "' is not a known element:\n1. If '" + tagName + "' is a Jaspring component, then verify that it is annotated with @Component and scanned.\n2. If '" + tagName + "' is a Web Component then add 'CUSTOM_ELEMENTS_SCHEMA' to the '@Component.schemas' of this component to suppress this message.",
                    element.startSourceSpan());
        }

        final var directives = structural ? List.<dev.isira.jaspring.component.DirectiveDef>of() : registry.matchDirectives(tagName, attributes, boundNames);
        for (final var input : element.inputs()) {
            expr(input.value(), scope);
            if (input.type() != BindingType.PROPERTY && input.type() != BindingType.TWO_WAY) continue;
            final var name = input.name();
            final var known = DIRECTIVE_INPUTS.contains(name)
                    || directives.stream().anyMatch(d -> d.inputs().containsKey(name))
                    || (component != null && component.inputs().containsKey(name))
                    || schema.hasProperty(component == null ? schemaName : "unknown", name)
                    || def.schemas().contains(Schema.NO_ERRORS)
                    || (tagName.contains("-") && def.schemas().contains(Schema.CUSTOM_ELEMENTS));
            if (!known) {
                final var hint = component != null
                        ? "\n1. If '" + name + "' is an input of '" + component.name() + "', then verify that it is declared with @Input or Signals.input()."
                        : tagName.contains("-")
                                ? "\n1. If '" + tagName + "' is a Jaspring component and it has '" + name + "' input, then verify that it is scanned.\n2. If '" + tagName + "' is a Web Component then add 'CUSTOM_ELEMENTS_SCHEMA' to the '@Component.schemas' of this component to suppress this message."
                                : "";
                error("Can't bind to '" + name + "' since it isn't a known property of '" + tagName + "'." + hint, input.keySpan() != null ? input.keySpan() : input.sourceSpan());
            }
        }
        if (component != null) {
            for (final var required : component.inputs().values()) {
                if (required.required() && !boundNames.contains(required.name()) && !attributes.containsKey(required.name())) {
                    error("Required input '" + required.name() + "' from component " + component.name() + " must be specified.", element.startSourceSpan());
                }
            }
        }
        for (final var output : element.outputs()) {
            final var handlerScope = new HashSet<>(scope);
            handlerScope.add("$event");
            expr(output.handler(), handlerScope);
        }
        visitChildren(element.children(), scope);
    }

    private void visitChildren(List<TemplateNode> children, Set<String> scope) {
        for (final var child : children) {
            visit(child, scope);
        }
    }

    private void template(TemplateNode.Template template, Set<String> scope) {
        for (final var attribute : template.templateAttrs()) {
            if (attribute instanceof TemplateNode.BoundAttribute bound) expr(bound.value(), scope);
        }
        for (final var input : template.inputs()) {
            expr(input.value(), scope);
        }
        for (final var output : template.outputs()) {
            final var handlerScope = new HashSet<>(scope);
            handlerScope.add("$event");
            expr(output.handler(), handlerScope);
        }
        final var inner = new HashSet<>(scope);
        template.variables().forEach(v -> inner.add(v.name()));
        visitView(template.children(), inner);
    }

    // ---------------- expressions ----------------

    private void expr(Expr expr, Set<String> scope) {
        switch (expr) {
            case Expr.PropertyRead p when p.receiver() instanceof Expr.ImplicitReceiver -> checkMember(p.name(), p.nameSpan(), scope);
            case Expr.PropertyRead p -> expr(p.receiver(), scope);
            case Expr.SafePropertyRead p -> expr(p.receiver(), scope);
            case Expr.KeyedRead k -> {
                expr(k.receiver(), scope);
                expr(k.key(), scope);
            }
            case Expr.SafeKeyedRead k -> {
                expr(k.receiver(), scope);
                expr(k.key(), scope);
            }
            case Expr.Call c -> {
                expr(c.receiver(), scope);
                c.args().forEach(a -> expr(a, scope));
            }
            case Expr.SafeCall c -> {
                expr(c.receiver(), scope);
                c.args().forEach(a -> expr(a, scope));
            }
            case Expr.BindingPipe p -> {
                if (!registry.hasPipe(p.name())) {
                    error("The pipe '" + p.name() + "' could not be found in the '" + def.name() + "' component. Verify that it is annotated with @Pipe and scanned.", p.nameSpan().absoluteStart(), p.nameSpan().absoluteEnd());
                }
                expr(p.exp(), scope);
                p.args().forEach(a -> expr(a, scope));
            }
            case Expr.Chain c -> c.expressions().forEach(e -> expr(e, scope));
            case Expr.Conditional c -> {
                expr(c.condition(), scope);
                expr(c.trueExp(), scope);
                expr(c.falseExp(), scope);
            }
            case Expr.Binary b -> {
                expr(b.left(), scope);
                expr(b.right(), scope);
            }
            case Expr.Unary u -> expr(u.expr(), scope);
            case Expr.PrefixNot p -> expr(p.expression(), scope);
            case Expr.TypeofExpression t -> expr(t.expression(), scope);
            case Expr.VoidExpression v -> expr(v.expression(), scope);
            case Expr.NonNullAssert n -> expr(n.expression(), scope);
            case Expr.ParenthesizedExpression p -> expr(p.expression(), scope);
            case Expr.Interpolation i -> i.expressions().forEach(e -> expr(e, scope));
            case Expr.LiteralArray a -> a.expressions().forEach(e -> expr(e, scope));
            case Expr.SpreadElement s -> expr(s.expression(), scope);
            case Expr.LiteralMap m -> m.values().forEach(v -> expr(v, scope));
            case Expr.TemplateLiteral t -> t.expressions().forEach(e -> expr(e, scope));
            case Expr.TaggedTemplateLiteral t -> {
                expr(t.tag(), scope);
                expr(t.template(), scope);
            }
            case Expr.ArrowFunction f -> {
                final var inner = new HashSet<>(scope);
                inner.addAll(f.parameters());
                expr(f.body(), inner);
            }
            case Expr.EmptyExpr e -> { }
            case Expr.ImplicitReceiver e -> { }
            case Expr.ThisReceiver e -> { }
            case Expr.LiteralPrimitive e -> { }
            case Expr.RegularExpressionLiteral e -> { }
        }
    }

    private void checkMember(String name, ExprSpan span, Set<String> scope) {
        if (scope.contains(name) || KNOWN_GLOBALS.contains(name) || MemberAccessor.hasMember(def.type(), name)) return;
        error("Property '" + name + "' does not exist on type '" + def.name() + "'.", span.absoluteStart(), span.absoluteEnd());
    }

    private void error(String message, SourceSpan span) {
        error(message, span == null ? 0 : span.start(), span == null ? 0 : span.end());
    }

    private void error(String message, int start, int end) {
        var line = 0;
        var col = 0;
        for (var i = 0; i < Math.min(start, source.length()); i++) {
            if (source.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        final var before = source.substring(Math.max(0, start - 30), Math.min(start, source.length()));
        final var after = source.substring(Math.min(start, source.length()), Math.min(source.length(), start + 30));
        final var text = message + " (\"" + before + "[ERROR ->]" + after + "\"): " + url + "@" + line + ":" + col;
        errors.add(new TemplateError(message, text, url, line, col, start, end, TemplateError.Level.ERROR));
    }

    private static String localName(String name) {
        if (!name.startsWith(":")) return name;
        final var second = name.indexOf(':', 1);
        final var namespace = name.substring(1, second);
        final var local = name.substring(second + 1);
        return namespace.equals("svg") || namespace.equals("math") ? local : namespace + ":" + local;
    }
}
