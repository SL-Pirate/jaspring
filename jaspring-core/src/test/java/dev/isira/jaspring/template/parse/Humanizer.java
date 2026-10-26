package dev.isira.jaspring.template.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.isira.jaspring.template.ast.DeferredTrigger;
import dev.isira.jaspring.template.ast.TemplateNode;

/// Port of Angular's `R3AstHumanizer`: flattens the tree into rows that match the spec expectations.
final class Humanizer {

    private final List<List<Object>> rows = new ArrayList<>();

    static List<List<Object>> humanize(List<? extends TemplateNode> nodes) {
        final var humanizer = new Humanizer();
        humanizer.visitAll(nodes);
        return humanizer.rows;
    }

    private void visitAll(List<? extends TemplateNode> nodes) {
        for (final var node : nodes) {
            visit(node);
        }
    }

    private void row(Object... values) {
        rows.add(new ArrayList<>(Arrays.asList(values)));
    }

    private void visit(TemplateNode node) {
        switch (node) {
            case TemplateNode.Element e -> {
                if (e.isSelfClosing()) row("Element", e.name(), "#selfClosing"); else row("Element", e.name());
                visitAll(e.attributes());
                visitAll(e.inputs());
                visitAll(e.outputs());
                visitAll(e.references());
                visitAll(e.children());
            }
            case TemplateNode.Template t -> {
                if (t.isSelfClosing()) row("Template", "#selfClosing"); else row("Template");
                visitAll(t.attributes());
                visitAll(t.inputs());
                visitAll(t.outputs());
                visitAll(t.templateAttrs());
                visitAll(t.references());
                visitAll(t.variables());
                visitAll(t.children());
            }
            case TemplateNode.Content c -> {
                if (c.isSelfClosing()) row("Content", c.selector(), "#selfClosing"); else row("Content", c.selector());
                visitAll(c.attributes());
                visitAll(c.children());
            }
            case TemplateNode.Variable v -> row("Variable", v.name(), v.value());
            case TemplateNode.Reference r -> row("Reference", r.name(), r.value());
            case TemplateNode.TextAttribute a -> row("TextAttribute", a.name(), a.value());
            case TemplateNode.BoundAttribute a -> row("BoundAttribute", a.type().ordinal(), a.name(), Unparser.unparse(a.value()));
            case TemplateNode.BoundEvent e -> row("BoundEvent", e.type().ordinal(), e.name(), e.target(), Unparser.unparse(e.handler()));
            case TemplateNode.Text t -> row("Text", t.value());
            case TemplateNode.BoundText t -> row("BoundText", Unparser.unparse(t.value()));
            case TemplateNode.Icu i -> {}
            case TemplateNode.DeferredBlock d -> {
                row("DeferredBlock");
                d.hydrateTriggers().forEach(this::trigger);
                d.triggers().forEach(this::trigger);
                d.prefetchTriggers().forEach(this::trigger);
                visitAll(d.children());
                if (d.placeholder() != null) visit(d.placeholder());
                if (d.loading() != null) visit(d.loading());
                if (d.error() != null) visit(d.error());
            }
            case TemplateNode.DeferredBlockPlaceholder p -> {
                if (p.minimumTime() != null) row("DeferredBlockPlaceholder", "minimum " + p.minimumTime() + "ms");
                else row("DeferredBlockPlaceholder");
                visitAll(p.children());
            }
            case TemplateNode.DeferredBlockLoading l -> {
                final var values = new ArrayList<Object>(List.of("DeferredBlockLoading"));
                if (l.afterTime() != null) values.add("after " + l.afterTime() + "ms");
                if (l.minimumTime() != null) values.add("minimum " + l.minimumTime() + "ms");
                rows.add(values);
                visitAll(l.children());
            }
            case TemplateNode.DeferredBlockError e -> {
                row("DeferredBlockError");
                visitAll(e.children());
            }
            case TemplateNode.SwitchBlock s -> {
                row("SwitchBlock", Unparser.unparse(s.expression()));
                visitAll(s.groups());
                if (s.exhaustiveCheck() != null) visit(s.exhaustiveCheck());
            }
            case TemplateNode.SwitchBlockCase c -> row("SwitchBlockCase", c.expression() == null ? null : Unparser.unparse(c.expression()));
            case TemplateNode.SwitchBlockCaseGroup g -> {
                row("SwitchBlockCaseGroup");
                visitAll(g.cases());
                visitAll(g.children());
            }
            case TemplateNode.SwitchExhaustiveCheck c -> row("SwitchExhaustiveCheck");
            case TemplateNode.ForLoopBlock f -> {
                row("ForLoopBlock", Unparser.unparse(f.expression()), Unparser.unparse(f.trackBy()));
                visit(f.item());
                visitAll(f.contextVariables());
                visitAll(f.children());
                if (f.empty() != null) visit(f.empty());
            }
            case TemplateNode.ForLoopBlockEmpty e -> {
                row("ForLoopBlockEmpty");
                visitAll(e.children());
            }
            case TemplateNode.IfBlock i -> {
                row("IfBlock");
                visitAll(i.branches());
            }
            case TemplateNode.IfBlockBranch b -> {
                row("IfBlockBranch", b.expression() == null ? null : Unparser.unparse(b.expression()));
                if (b.expressionAlias() != null) visit(b.expressionAlias());
                visitAll(b.children());
            }
            case TemplateNode.UnknownBlock u -> row("UnknownBlock", u.name());
            case TemplateNode.LetDeclaration l -> row("LetDeclaration", l.name(), Unparser.unparse(l.value()));
        }
    }

    private void trigger(DeferredTrigger trigger) {
        switch (trigger) {
            case DeferredTrigger.When w -> row("BoundDeferredTrigger", Unparser.unparse(w.value()));
            case DeferredTrigger.Immediate i -> row("ImmediateDeferredTrigger");
            case DeferredTrigger.Hover h -> row("HoverDeferredTrigger", h.reference());
            case DeferredTrigger.Idle i -> row("IdleDeferredTrigger");
            case DeferredTrigger.Timer t -> row("TimerDeferredTrigger", t.delay());
            case DeferredTrigger.Interaction i -> row("InteractionDeferredTrigger", i.reference());
            case DeferredTrigger.Viewport v -> {
                if (v.options() != null) row("ViewportDeferredTrigger", v.reference(), Unparser.unparse(v.options()));
                else row("ViewportDeferredTrigger", v.reference());
            }
            case DeferredTrigger.Never n -> row("NeverDeferredTrigger");
        }
    }
}
