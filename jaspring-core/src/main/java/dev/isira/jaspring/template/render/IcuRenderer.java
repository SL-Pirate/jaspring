package dev.isira.jaspring.template.render;

import java.util.Map;
import java.util.function.Consumer;

import dev.isira.jaspring.template.ast.I18nNode;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.Js;

/// Renders ICU expansions (`{count, plural, =0 {none} other {many}}`). Plural categories are English only for now:
/// `one` for exactly 1, `other` otherwise; CLDR data comes later.
final class IcuRenderer {

    private final TemplateNode.Icu icu;
    private final java.util.function.Function<TemplateNode, Object> evaluateSwitch;
    private final Consumer<TemplateNode> renderPlaceholder;
    private final HtmlWriter out;

    IcuRenderer(TemplateNode.Icu icu, java.util.function.Function<TemplateNode, Object> evaluateSwitch,
            Consumer<TemplateNode> renderPlaceholder, HtmlWriter out) {
        this.icu = icu;
        this.evaluateSwitch = evaluateSwitch;
        this.renderPlaceholder = renderPlaceholder;
        this.out = out;
    }

    void render() {
        if (icu.message() == null) return;
        renderIcu(icu.message());
    }

    private void renderIcu(I18nNode.Icu message) {
        final var switchNode = icu.vars().get(message.expressionPlaceholder());
        final var value = switchNode == null ? null : evaluateSwitch.apply(switchNode);
        final var selected = selectCase(message, value);
        if (selected != null) renderNode(selected);
    }

    private static I18nNode selectCase(I18nNode.Icu message, Object value) {
        final Map<String, I18nNode> cases = message.cases();
        if (message.type().equals("plural")) {
            final var number = Js.toNumber(value);
            for (final var entry : cases.entrySet()) {
                if (entry.getKey().startsWith("=") && Js.looseEquals(Js.toNumber(entry.getKey().substring(1)), number)) {
                    return entry.getValue();
                }
            }
            final var category = number.doubleValue() == 1 ? "one" : "other";
            final var match = cases.get(category);
            return match != null ? match : cases.get("other");
        }
        final var match = cases.get(Js.toJsString(value));
        return match != null ? match : cases.get("other");
    }

    private void renderNode(I18nNode node) {
        switch (node) {
            case I18nNode.Text text -> out.text(text.value());
            case I18nNode.Container container -> container.children().forEach(this::renderNode);
            case I18nNode.Placeholder placeholder -> {
                final var bound = icu.placeholders().get(placeholder.name());
                if (bound != null) renderPlaceholder.accept(bound);
            }
            case I18nNode.IcuPlaceholder nested -> renderIcu(nested.value());
            case I18nNode.TagPlaceholder tag -> {
                out.openTag(tag.tag());
                tag.attrs().forEach(out::attribute);
                out.closeStartTag();
                if (!tag.isVoid()) {
                    tag.children().forEach(this::renderNode);
                    out.closeTag(tag.tag());
                }
            }
            case I18nNode.BlockPlaceholder block -> block.children().forEach(this::renderNode);
            case I18nNode.Icu icuNode -> renderIcu(icuNode);
        }
    }
}
