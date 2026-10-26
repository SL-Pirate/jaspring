package dev.isira.jaspring.template.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.template.ast.EventType;
import dev.isira.jaspring.template.ast.SecurityContext;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.EvaluationContext;
import dev.isira.jaspring.template.eval.EvaluationException;
import dev.isira.jaspring.template.eval.ExpressionEvaluator;
import dev.isira.jaspring.template.eval.Js;
import dev.isira.jaspring.template.eval.JsFunction;
import dev.isira.jaspring.template.eval.Scope;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.expr.JsUndefined;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.security.DomSanitizer;

/// Walks the template AST and writes HTML. One instance per application; per-render state lives in frames.
public final class TemplateRenderer {

    /// Inputs consumed by built-in directives; they never reflect to attributes.
    private static final Set<String> DIRECTIVE_INPUTS = Set.of(
            "ngClass", "ngStyle", "ngSwitch", "ngTemplateOutlet", "ngTemplateOutletContext", "ngTemplateOutletInjector",
            "ngModel", "ngModelOptions", "ngNonBindable");

    private final RenderHost host;
    private final PropertyReflection reflection;

    public TemplateRenderer(DomSchema schema, RenderHost host) {
        this.host = host;
        this.reflection = new PropertyReflection(schema);
    }

    public String render(List<TemplateNode> nodes, EvaluationContext context) {
        final var out = new HtmlWriter();
        render(nodes, context, out);
        return out.toString();
    }

    public void render(List<TemplateNode> nodes, EvaluationContext context, HtmlWriter out) {
        new Frame(context, out, "", null).renderView(nodes);
    }

    /// Tracks which `@switch`/`[ngSwitch]` the current nodes belong to and whether any case already matched.
    private static final class SwitchState {
        final Object value;
        final boolean anyCaseMatches;

        SwitchState(Object value, boolean anyCaseMatches) {
            this.value = value;
            this.anyCaseMatches = anyCaseMatches;
        }
    }

    /// One scope level of rendering. `viewKey` identifies the embedded view (block body, loop iteration) so child
    /// component instances can be matched across renders.
    private final class Frame {

        private final EvaluationContext context;
        private final HtmlWriter out;
        private final String viewKey;
        private final SwitchState switchState;

        Frame(EvaluationContext context, HtmlWriter out, String viewKey, SwitchState switchState) {
            this.context = context;
            this.out = out;
            this.viewKey = viewKey;
            this.switchState = switchState;
        }

        Frame view(Map<String, Object> locals, String keySegment) {
            return new Frame(context.withScope(context.scope().child(locals)), out, viewKey + keySegment, switchState);
        }

        Frame withSwitch(SwitchState state) {
            return new Frame(context, out, viewKey, state);
        }

        void renderView(List<TemplateNode> nodes) {
            view(Map.of(), "").renderEmbedded(nodes);
        }

        /// Starts an embedded view: template references are hoisted first, so `else` templates may follow their `*ngIf`.
        void renderEmbedded(List<TemplateNode> nodes) {
            hoistTemplateRefs(nodes);
            renderChildren(nodes);
        }

        private void hoistTemplateRefs(List<TemplateNode> nodes) {
            for (final var node : nodes) {
                switch (node) {
                    case TemplateNode.Template template -> {
                        for (final var reference : template.references()) {
                            context.scope().define(reference.name(), new TemplateRef(template.children(), template.variables(), context.scope()));
                        }
                    }
                    case TemplateNode.Element element -> hoistTemplateRefs(element.children());
                    default -> { }
                }
            }
        }

        void renderChildren(List<TemplateNode> nodes) {
            for (final var node : nodes) {
                render(node);
            }
        }

        private void render(TemplateNode node) {
            switch (node) {
                case TemplateNode.Text text -> out.text(text.value());
                case TemplateNode.BoundText text -> out.text(Js.renderStringify(eval(text.value())));
                case TemplateNode.Element element -> element(element);
                case TemplateNode.LetDeclaration let -> context.scope().define(let.name(), eval(let.value()));
                case TemplateNode.Template template -> template(template);
                case TemplateNode.IfBlock block -> ifBlock(block);
                case TemplateNode.ForLoopBlock block -> forBlock(block);
                case TemplateNode.SwitchBlock block -> switchBlock(block);
                case TemplateNode.DeferredBlock block -> deferBlock(block);
                case TemplateNode.Icu icu -> new IcuRenderer(icu, this::switchValue, this::render, out).render();
                case TemplateNode.Content content -> throw new UnsupportedOperationException("ng-content rendering is not implemented yet");
                case TemplateNode.UnknownBlock block -> throw new IllegalStateException("Unknown block @" + block.name());
                default -> throw new IllegalStateException("Unexpected node " + node.getClass().getSimpleName());
            }
        }

        private Object eval(Expr expr) {
            return ExpressionEvaluator.evaluate(expr, context);
        }

        // An ICU switch variable is a BoundText interpolating exactly the switch expression; evaluate that expression raw.
        private Object switchValue(TemplateNode node) {
            if (node instanceof TemplateNode.BoundText bound && bound.value() instanceof Expr.Interpolation interpolation
                    && interpolation.expressions().size() == 1) {
                return eval(interpolation.expressions().get(0));
            }
            return node instanceof TemplateNode.BoundText bound ? eval(bound.value()) : node instanceof TemplateNode.Text text ? text.value() : null;
        }

        // ---------------- elements ----------------

        private void element(TemplateNode.Element element) {
            final var tagName = localName(element.name());
            final var directiveInputs = new LinkedHashMap<String, Object>();
            final var attributes = new LinkedHashMap<String, String>();
            final var styles = new StyleBindings();
            String innerHtml = null;
            String textContent = null;

            for (final var attribute : element.attributes()) {
                final var name = localName(attribute.name());
                switch (name) {
                    case "class" -> styles.staticClass(attribute.value());
                    case "style" -> styles.staticStyle(attribute.value());
                    default -> attributes.put(name, attribute.value());
                }
            }

            for (final var input : element.inputs()) {
                final var value = eval(input.value());
                switch (input.type()) {
                    case PROPERTY, TWO_WAY -> {
                        switch (input.name()) {
                            case "class", "className" -> styles.classValue(value);
                            case "style" -> styles.styleValue(value);
                            case "ngClass" -> styles.classValue(value);
                            case "ngStyle" -> styles.styleValue(value);
                            default -> {
                                if (DIRECTIVE_INPUTS.contains(input.name())) {
                                    directiveInputs.put(input.name(), value);
                                    continue;
                                }
                                final var reflected = reflection.reflect(tagName, input.name());
                                switch (reflected.kind()) {
                                    case INNER_HTML -> innerHtml = DomSanitizer.INSTANCE.sanitize(SecurityContext.HTML, value);
                                    case TEXT_CONTENT -> textContent = Js.renderStringify(value);
                                    case BOOLEAN_ATTRIBUTE -> {
                                        if (Js.truthy(value)) attributes.put(reflected.attribute(), ""); else attributes.remove(reflected.attribute());
                                    }
                                    case ATTRIBUTE -> {
                                        final var text = DomSanitizer.INSTANCE.sanitize(input.securityContext(), value);
                                        if (text == null) attributes.remove(reflected.attribute()); else attributes.put(reflected.attribute(), text);
                                    }
                                    case SKIP -> { }
                                }
                            }
                        }
                    }
                    case ATTRIBUTE -> {
                        final var text = DomSanitizer.INSTANCE.sanitize(input.securityContext(), value);
                        final var name = localName(input.name());
                        if (text == null) attributes.remove(name); else attributes.put(name, text);
                    }
                    case CLASS -> styles.toggle(input.name(), Js.truthy(value));
                    case STYLE -> styles.style(input.name(), input.unit(), value);
                    case LEGACY_ANIMATION, ANIMATION -> { }
                }
            }

            final var isContainer = tagName.equals("ng-container");
            if (!isContainer) {
                final var eventMarkers = new ArrayList<String>();
                for (final var output : element.outputs()) {
                    if (output.type() == EventType.LEGACY_ANIMATION || output.type() == EventType.ANIMATION) continue;
                    final var ref = host.registerHandler(output, context.scope());
                    if (ref == null) continue;
                    final var target = output.target() == null ? "" : output.target() + ":";
                    eventMarkers.add(target + output.name() + ":" + ref + HandlerFlags.analyze(output.handler()).suffix());
                }

                out.openTag(tagName);
                final var classAttribute = styles.classAttribute();
                final var styleAttribute = styles.styleAttribute();
                attributes.forEach(out::attribute);
                if (classAttribute != null) out.attribute("class", classAttribute);
                if (styleAttribute != null) out.attribute("style", styleAttribute);
                if (host.contentAttribute() != null) out.attribute(host.contentAttribute(), "");
                if (!eventMarkers.isEmpty()) out.attribute("data-jsp-on", String.join(" ", eventMarkers));
                out.closeStartTag();
            }

            for (final var reference : element.references()) {
                context.scope().define(reference.name(), new ElementRef(tagName, Map.copyOf(attributes)));
            }

            if (element.isVoid()) return;
            if (innerHtml != null) {
                out.raw(innerHtml);
            } else if (textContent != null) {
                out.text(textContent);
            } else if (directiveInputs.containsKey("ngTemplateOutlet")) {
                outlet(directiveInputs.get("ngTemplateOutlet"), directiveInputs.get("ngTemplateOutletContext"));
            } else {
                final var frame = directiveInputs.containsKey("ngSwitch")
                        ? withSwitch(new SwitchState(directiveInputs.get("ngSwitch"), anySwitchCaseMatches(element.children(), directiveInputs.get("ngSwitch"))))
                        : this;
                frame.renderChildren(element.children());
            }
            if (!isContainer) out.closeTag(tagName);
        }

        // ---------------- <ng-template> and structural directives ----------------

        private void template(TemplateNode.Template template) {
            final var directives = new LinkedHashMap<String, TemplateNode.Attribute>();
            for (final var attribute : template.templateAttrs()) {
                directives.put(attribute.name(), attribute);
            }
            for (final var input : template.inputs()) {
                directives.putIfAbsent(input.name(), input);
            }
            for (final var attribute : template.attributes()) {
                directives.putIfAbsent(attribute.name(), attribute);
            }

            for (final var reference : template.references()) {
                context.scope().define(reference.name(), new TemplateRef(template.children(), template.variables(), context.scope()));
            }

            final var directive = directives.keySet().stream()
                    .filter(name -> name.equals("ngIf") || name.equals("ngFor") || name.equals("ngForOf") || name.equals("ngSwitchCase")
                            || name.equals("ngSwitchDefault") || name.equals("ngTemplateOutlet"))
                    .findFirst()
                    .orElse(null);
            if (directive == null) {
                if (template.templateAttrs().isEmpty()) return;
                throw new EvaluationException("Property binding " + template.templateAttrs().get(0).name()
                        + " not used by any directive on an embedded template");
            }
            switch (directive) {
                case "ngIf" -> ngIf(template, directives);
                case "ngFor", "ngForOf" -> ngFor(template, directives);
                case "ngSwitchCase" -> {
                    if (switchState != null && Js.strictEquals(directiveValue(directives.get("ngSwitchCase")), switchState.value)) {
                        embedded(template, Map.of(), "/case" + template.sourceSpan().start());
                    }
                }
                case "ngSwitchDefault" -> {
                    if (switchState != null && !switchState.anyCaseMatches) {
                        embedded(template, Map.of(), "/default" + template.sourceSpan().start());
                    }
                }
                case "ngTemplateOutlet" -> outlet(directiveValue(directives.get("ngTemplateOutlet")),
                        directives.containsKey("ngTemplateOutletContext") ? directiveValue(directives.get("ngTemplateOutletContext")) : null);
                default -> throw new IllegalStateException(directive);
            }
        }

        private Object directiveValue(TemplateNode.Attribute attribute) {
            return switch (attribute) {
                case null -> JsUndefined.INSTANCE;
                case TemplateNode.TextAttribute text -> text.value();
                case TemplateNode.BoundAttribute bound -> eval(bound.value());
            };
        }

        private void ngIf(TemplateNode.Template template, Map<String, TemplateNode.Attribute> directives) {
            final var condition = directiveValue(directives.get("ngIf"));
            final var locals = new LinkedHashMap<String, Object>();
            locals.put("$implicit", condition);
            locals.put("ngIf", condition);
            if (Js.truthy(condition)) {
                final var then = directives.containsKey("ngIfThen") ? directiveValue(directives.get("ngIfThen")) : null;
                if (then instanceof TemplateRef ref) renderTemplateRef(ref, locals, "/then" + template.sourceSpan().start());
                else embedded(template, locals, "/if" + template.sourceSpan().start());
            } else if (directives.containsKey("ngIfElse") && directiveValue(directives.get("ngIfElse")) instanceof TemplateRef ref) {
                renderTemplateRef(ref, locals, "/else" + template.sourceSpan().start());
            }
        }

        private void ngFor(TemplateNode.Template template, Map<String, TemplateNode.Attribute> directives) {
            final var items = toList(directiveValue(directives.get("ngForOf")));
            final var trackBy = directives.containsKey("ngForTrackBy") ? directiveValue(directives.get("ngForTrackBy")) : null;
            for (var i = 0; i < items.size(); i++) {
                final var item = items.get(i);
                final var locals = loopLocals(item, i, items.size());
                locals.put("ngForOf", items);
                final var key = trackBy instanceof JsFunction fn ? fn.call((long) i, item) : item;
                embedded(template, locals, "/for" + template.sourceSpan().start() + "[" + trackKey(key) + "]");
            }
        }

        /// Renders a structural directive's children with the `let-x="key"` variables resolved from `locals`.
        private void embedded(TemplateNode.Template template, Map<String, Object> locals, String keySegment) {
            final var scope = new LinkedHashMap<String, Object>();
            for (final var variable : template.variables()) {
                scope.put(variable.name(), locals.getOrDefault(variable.value().isEmpty() ? "$implicit" : variable.value(), JsUndefined.INSTANCE));
            }
            view(scope, keySegment).renderEmbedded(template.children());
        }

        private void outlet(Object target, Object contextValue) {
            if (Js.isNullish(target)) return;
            if (!(target instanceof TemplateRef ref)) {
                throw new EvaluationException("ngTemplateOutlet expects a TemplateRef, got " + target.getClass().getSimpleName());
            }
            final var locals = new LinkedHashMap<String, Object>();
            if (contextValue instanceof Map<?, ?> map) {
                map.forEach((k, v) -> locals.put(Js.toJsString(k), v));
            }
            renderTemplateRef(ref, locals, "/outlet" + System.identityHashCode(ref.nodes()));
        }

        private void renderTemplateRef(TemplateRef ref, Map<String, Object> locals, String keySegment) {
            final var scope = new LinkedHashMap<String, Object>();
            for (final var variable : ref.variables()) {
                scope.put(variable.name(), locals.getOrDefault(variable.value().isEmpty() ? "$implicit" : variable.value(), JsUndefined.INSTANCE));
            }
            // The outlet renders in the declaring view's scope, not the caller's, like Angular.
            new Frame(context.withScope(ref.declarationScope().child(scope)), out, viewKey + keySegment, switchState)
                    .renderEmbedded(ref.nodes());
        }

        private boolean anySwitchCaseMatches(List<TemplateNode> nodes, Object value) {
            for (final var node : nodes) {
                switch (node) {
                    case TemplateNode.Template t -> {
                        for (final var attribute : t.templateAttrs()) {
                            if (attribute.name().equals("ngSwitchCase") && Js.strictEquals(directiveValue(attribute), value)) return true;
                        }
                    }
                    case TemplateNode.Element e -> {
                        final var nested = e.inputs().stream().anyMatch(input -> input.name().equals("ngSwitch"));
                        if (!nested && anySwitchCaseMatches(e.children(), value)) return true;
                    }
                    default -> { }
                }
            }
            return false;
        }

        // ---------------- control flow blocks ----------------

        private void ifBlock(TemplateNode.IfBlock block) {
            for (final var branch : block.branches()) {
                final var value = branch.expression() == null ? Boolean.TRUE : eval(branch.expression());
                if (!Js.truthy(value)) continue;
                final var locals = branch.expressionAlias() == null ? Map.<String, Object>of() : Map.of(branch.expressionAlias().name(), value);
                view(locals, "/if" + branch.sourceSpan().start()).renderEmbedded(branch.children());
                return;
            }
        }

        private void forBlock(TemplateNode.ForLoopBlock block) {
            final var items = toList(eval(block.expression()));
            if (items.isEmpty()) {
                if (block.empty() != null) view(Map.of(), "/empty" + block.sourceSpan().start()).renderEmbedded(block.empty().children());
                return;
            }
            for (var i = 0; i < items.size(); i++) {
                final var implicit = loopLocals(items.get(i), i, items.size());
                final var locals = new LinkedHashMap<String, Object>();
                locals.put(block.item().name(), items.get(i));
                for (final var variable : block.contextVariables()) {
                    locals.put(variable.name(), implicit.get(variable.value()));
                }
                final var iteration = view(locals, "");
                final var key = ExpressionEvaluator.evaluate(block.trackBy(), iteration.context);
                iteration.view(Map.of(), "/for" + block.sourceSpan().start() + "[" + trackKey(key) + "]").renderEmbedded(block.children());
            }
        }

        private void switchBlock(TemplateNode.SwitchBlock block) {
            final var value = eval(block.expression());
            TemplateNode.SwitchBlockCaseGroup defaultGroup = null;
            for (final var group : block.groups()) {
                for (final var caseBlock : group.cases()) {
                    if (caseBlock.expression() == null) {
                        defaultGroup = group;
                    } else if (Js.strictEquals(eval(caseBlock.expression()), value)) {
                        view(Map.of(), "/case" + group.sourceSpan().start()).renderEmbedded(group.children());
                        return;
                    }
                }
            }
            if (defaultGroup != null) {
                view(Map.of(), "/case" + defaultGroup.sourceSpan().start()).renderEmbedded(defaultGroup.children());
            }
        }

        // Like Angular SSR: the placeholder renders on the server, unless hydrate triggers ask for the real content.
        private void deferBlock(TemplateNode.DeferredBlock block) {
            if (!block.hydrateTriggers().isEmpty()) {
                view(Map.of(), "/defer" + block.sourceSpan().start()).renderEmbedded(block.children());
            } else if (block.placeholder() != null) {
                view(Map.of(), "/placeholder" + block.sourceSpan().start()).renderEmbedded(block.placeholder().children());
            }
        }

        private static LinkedHashMap<String, Object> loopLocals(Object item, int index, int count) {
            final var locals = new LinkedHashMap<String, Object>();
            locals.put("$implicit", item);
            locals.put("$index", (long) index);
            locals.put("$first", index == 0);
            locals.put("$last", index == count - 1);
            locals.put("$even", index % 2 == 0);
            locals.put("$odd", index % 2 == 1);
            locals.put("$count", (long) count);
            locals.put("index", (long) index);
            locals.put("first", index == 0);
            locals.put("last", index == count - 1);
            locals.put("even", index % 2 == 0);
            locals.put("odd", index % 2 == 1);
            locals.put("count", (long) count);
            return locals;
        }
    }

    static List<Object> toList(Object value) {
        return switch (value) {
            case null -> List.of();
            case JsUndefined u -> List.of();
            case List<?> list -> new ArrayList<>(list);
            case Iterable<?> iterable -> {
                final var list = new ArrayList<Object>();
                iterable.forEach(list::add);
                yield list;
            }
            case Map<?, ?> map -> new ArrayList<>(map.entrySet());
            case CharSequence s -> throw new EvaluationException("Cannot iterate over a string; use a list");
            default -> {
                if (value.getClass().isArray()) yield Js.arrayAsList(value);
                throw new EvaluationException("Cannot find a differ supporting object '" + value + "' of type '" + value.getClass().getSimpleName()
                        + "'. Only arrays and iterables are allowed");
            }
        };
    }

    /// A stable text form of a track value: primitives by value, objects by identity.
    static String trackKey(Object key) {
        if (Js.isNullish(key) || key instanceof Number || key instanceof CharSequence || key instanceof Boolean || key instanceof Character
                || key instanceof Enum<?>) {
            return Js.toJsString(key);
        }
        return key.getClass().isRecord() ? "r:" + key.hashCode() : "o:" + System.identityHashCode(key);
    }

    /// Angular names namespaced elements and attributes `:svg:circle` / `:xlink:href`.
    static String localName(String name) {
        if (!name.startsWith(":")) return name;
        final var second = name.indexOf(':', 1);
        final var namespace = name.substring(1, second);
        final var local = name.substring(second + 1);
        return namespace.equals("svg") || namespace.equals("math") ? local : namespace + ":" + local;
    }
}
