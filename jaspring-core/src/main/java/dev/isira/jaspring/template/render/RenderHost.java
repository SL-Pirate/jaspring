package dev.isira.jaspring.template.render;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.component.ComponentDef;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.EvaluationContext;
import dev.isira.jaspring.template.eval.Scope;

/// What the renderer needs from its surroundings: event registration, style scoping, child components and
/// content projection. The view layer implements it; the defaults render plain HTML with none of that.
public interface RenderHost {

    RenderHost NONE = new RenderHost() {};

    /// Registers an event handler occurrence and returns its page-unique reference (`viewId.seq`),
    /// or `null` when events are not live (no marker is rendered).
    default String registerHandler(TemplateNode.BoundEvent event, Scope scope) {
        return null;
    }

    /// The `_ngcontent-*` attribute added to every element of the current component, or `null` without style scoping.
    default String contentAttribute() {
        return null;
    }

    /// The component whose selector matches this element, or `null` for a plain element.
    default ComponentDef matchComponent(String tagName, Map<String, String> attributes, Set<String> boundNames) {
        return null;
    }

    /// Renders a component occurrence. Returns the text to write in its place (normally a slot marker) and the instance,
    /// which `#ref` on the element resolves to.
    default RenderedComponent renderComponent(ComponentOccurrence occurrence) {
        throw new UnsupportedOperationException("This host does not render components");
    }

    /// Renders the content projected into `<ng-content select="...">`. Returns false when nothing was projected,
    /// so the fallback children can render instead.
    default boolean renderContent(String selector, HtmlWriter out) {
        return false;
    }

    record RenderedComponent(String markup, Object instance) {}

    /// Everything the parent's template says about one component element. `projection` renders the element's
    /// children later, in the parent's scope, when the child template reaches `<ng-content>`.
    record ComponentOccurrence(
            ComponentDef def,
            TemplateNode.Element element,
            String viewKey,
            Map<String, Object> inputs,
            Map<String, String> hostAttributes,
            List<TemplateNode.BoundEvent> outputs,
            List<String> nativeEventMarkers,
            List<TemplateNode> projectedNodes,
            Projection projection,
            Scope scope) {}

    /// A deferred render of projected nodes with the declaring component's context.
    @FunctionalInterface
    interface Projection {
        void render(List<TemplateNode> nodes, HtmlWriter out);
    }
}
