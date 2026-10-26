package dev.isira.jaspring.template.render;

import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.Scope;

/// What the renderer needs from its surroundings. The view layer implements it; tests use the defaults.
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
}
