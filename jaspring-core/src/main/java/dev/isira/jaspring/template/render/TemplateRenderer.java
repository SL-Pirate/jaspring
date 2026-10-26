package dev.isira.jaspring.template.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.isira.jaspring.template.ast.BindingType;
import dev.isira.jaspring.template.ast.SecurityContext;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.EvaluationContext;
import dev.isira.jaspring.template.eval.ExpressionEvaluator;
import dev.isira.jaspring.template.eval.Js;
import dev.isira.jaspring.template.eval.Scope;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.security.DomSanitizer;

/// Walks the template AST and writes HTML. One instance per application; state lives in the frames.
public final class TemplateRenderer {

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
        new Frame(context, out).renderView(nodes);
    }

    /// One scope level of rendering: a template, a block body, or an element's children.
    private final class Frame {

        private final EvaluationContext context;
        private final HtmlWriter out;

        Frame(EvaluationContext context, HtmlWriter out) {
            this.context = context;
            this.out = out;
        }

        Frame child(Scope scope) {
            return new Frame(context.withScope(scope), out);
        }

        /// Renders an embedded view: a new scope that `#refs` and `@let` declarations define into.
        void renderView(List<TemplateNode> nodes) {
            child(context.scope().child(Map.of())).renderChildren(nodes);
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
                case TemplateNode.Content content -> throw unsupported("ng-content");
                case TemplateNode.Template template -> throw unsupported("ng-template");
                case TemplateNode.IfBlock block -> throw unsupported("@if");
                case TemplateNode.ForLoopBlock block -> throw unsupported("@for");
                case TemplateNode.SwitchBlock block -> throw unsupported("@switch");
                case TemplateNode.DeferredBlock block -> throw unsupported("@defer");
                case TemplateNode.Icu icu -> throw unsupported("ICU");
                case TemplateNode.UnknownBlock block -> throw new IllegalStateException("Unknown block @" + block.name());
                default -> throw new IllegalStateException("Unexpected node " + node.getClass().getSimpleName());
            }
        }

        private Object eval(Expr expr) {
            return ExpressionEvaluator.evaluate(expr, context);
        }

        private void element(TemplateNode.Element element) {
            final var tagName = localName(element.name());
            if (tagName.equals("ng-container")) {
                renderChildren(element.children());
                return;
            }
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
                        if (input.name().equals("class") || input.name().equals("className")) {
                            styles.classValue(value);
                        } else if (input.name().equals("style")) {
                            styles.styleValue(value);
                        } else if (input.name().equals("ngClass")) {
                            styles.classValue(value);
                        } else if (input.name().equals("ngStyle")) {
                            styles.styleValue(value);
                        } else {
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

            final var eventMarkers = new ArrayList<String>();
            for (final var output : element.outputs()) {
                if (output.type() == dev.isira.jaspring.template.ast.EventType.LEGACY_ANIMATION
                        || output.type() == dev.isira.jaspring.template.ast.EventType.ANIMATION) continue;
                final var ref = host.registerHandler(output, context.scope());
                if (ref == null) continue;
                final var target = output.target() == null ? "" : output.target() + ":";
                eventMarkers.add(target + output.name() + ":" + ref + HandlerFlags.analyze(output.handler()).suffix());
            }

            out.openTag(tagName);
            final var classAttribute = styles.classAttribute();
            final var styleAttribute = styles.styleAttribute();
            attributes.forEach((name, value) -> out.attribute(name, value));
            if (classAttribute != null) out.attribute("class", classAttribute);
            if (styleAttribute != null) out.attribute("style", styleAttribute);
            if (host.contentAttribute() != null) out.attribute(host.contentAttribute(), "");
            if (!eventMarkers.isEmpty()) out.attribute("data-jsp-on", String.join(" ", eventMarkers));
            out.closeStartTag();

            for (final var reference : element.references()) {
                context.scope().define(reference.name(), new ElementRef(tagName, Map.copyOf(attributes)));
            }

            if (element.isVoid()) return;
            if (innerHtml != null) {
                out.raw(innerHtml);
            } else if (textContent != null) {
                out.text(textContent);
            } else {
                renderChildren(element.children());
            }
            out.closeTag(tagName);
        }
    }

    /// Angular names namespaced elements and attributes `:svg:circle` / `:xlink:href`.
    static String localName(String name) {
        if (!name.startsWith(":")) return name;
        final var second = name.indexOf(':', 1);
        final var namespace = name.substring(1, second);
        final var local = name.substring(second + 1);
        return namespace.equals("svg") || namespace.equals("math") ? local : namespace + ":" + local;
    }

    private static UnsupportedOperationException unsupported(String feature) {
        return new UnsupportedOperationException(feature + " rendering is not implemented yet");
    }
}
