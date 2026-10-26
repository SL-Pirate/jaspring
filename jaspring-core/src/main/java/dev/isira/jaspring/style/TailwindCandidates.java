package dev.isira.jaspring.style;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.template.ast.BindingType;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.expr.Expr;

/// The class names Tailwind should generate utilities for. Tailwind's file scanner is replaced by a walk over
/// the compiled templates: static `class` attributes, `[class.x]` bindings, string literals and object keys in
/// `[class]`/`[ngClass]` bindings, directive `class.x` host bindings, and `class` attributes of extra HTML.
public final class TailwindCandidates {

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL);

    private final Set<String> candidates = new LinkedHashSet<>();

    public static Set<String> of(ComponentRegistry registry, String... extraHtml) {
        final var extractor = new TailwindCandidates();
        for (final var def : registry.components()) {
            extractor.nodes(registry.template(def.type()).parsed().nodes());
        }
        for (final var directive : registry.directives()) {
            directive.hostBindings().forEach(binding -> {
                if (binding.target().startsWith("class.")) extractor.classes(binding.target().substring(6));
            });
        }
        for (final var html : extraHtml) {
            extractor.html(html);
        }
        return Set.copyOf(extractor.candidates);
    }

    private void html(String html) {
        final var matcher = CLASS_ATTRIBUTE.matcher(html);
        while (matcher.find()) {
            classes(matcher.group(2));
        }
    }

    private void classes(String spaceSeparated) {
        for (final var name : spaceSeparated.trim().split("\\s+")) {
            if (!name.isEmpty()) candidates.add(name);
        }
    }

    private void nodes(List<TemplateNode> nodes) {
        for (final var node : nodes) {
            switch (node) {
                case TemplateNode.Element e -> {
                    e.attributes().forEach(a -> {
                        if (a.name().equals("class") || a.name().equals("ngClass")) classes(a.value());
                    });
                    e.inputs().forEach(this::input);
                    nodes(e.children());
                }
                case TemplateNode.Template t -> {
                    t.attributes().forEach(a -> {
                        if (a.name().equals("class")) classes(a.value());
                    });
                    t.inputs().forEach(this::input);
                    t.templateAttrs().forEach(a -> {
                        if (a instanceof TemplateNode.BoundAttribute bound) input(bound);
                    });
                    nodes(t.children());
                }
                case TemplateNode.Content c -> nodes(c.children());
                case TemplateNode.IfBlock b -> b.branches().forEach(branch -> nodes(branch.children()));
                case TemplateNode.ForLoopBlock b -> {
                    nodes(b.children());
                    if (b.empty() != null) nodes(b.empty().children());
                }
                case TemplateNode.SwitchBlock b -> b.groups().forEach(group -> nodes(group.children()));
                case TemplateNode.DeferredBlock b -> {
                    nodes(b.children());
                    if (b.placeholder() != null) nodes(b.placeholder().children());
                    if (b.loading() != null) nodes(b.loading().children());
                    if (b.error() != null) nodes(b.error().children());
                }
                default -> { }
            }
        }
    }

    private void input(TemplateNode.BoundAttribute input) {
        if (input.type() == BindingType.CLASS) {
            classes(input.name());
        } else if (input.name().equals("class") || input.name().equals("className") || input.name().equals("ngClass")) {
            literals(input.value());
        }
    }

    private void literals(Expr expr) {
        switch (expr) {
            case Expr.LiteralPrimitive p -> {
                if (p.value() instanceof String s) classes(s);
            }
            case Expr.LiteralMap m -> {
                m.keys().forEach(key -> {
                    if (key instanceof Expr.MapKey.Property property) classes(property.key());
                });
                m.values().forEach(this::literals);
            }
            case Expr.LiteralArray a -> a.expressions().forEach(this::literals);
            case Expr.Conditional c -> {
                literals(c.trueExp());
                literals(c.falseExp());
            }
            case Expr.Binary b -> {
                literals(b.left());
                literals(b.right());
            }
            case Expr.ParenthesizedExpression p -> literals(p.expression());
            case Expr.Interpolation i -> {
                i.strings().forEach(this::classes);
                i.expressions().forEach(this::literals);
            }
            case Expr.TemplateLiteral t -> {
                t.elements().forEach(this::classes);
                t.expressions().forEach(this::literals);
            }
            default -> { }
        }
    }
}
