package dev.isira.jaspring.view;

import java.util.HashMap;
import java.util.Map;

import dev.isira.jaspring.component.ComponentDef;

/// All component views of one page, by id, with the root. Owns id allocation and HTML assembly.
public final class ViewTree {

    private final Map<Long, ComponentView> views = new HashMap<>();
    private long nextId;
    private ComponentView root;

    ComponentView create(ComponentDef def, Object instance, ComponentView parent) {
        final var view = new ComponentView(++nextId, def, instance, parent);
        views.put(view.id(), view);
        if (parent == null) root = view;
        return view;
    }

    void remove(ComponentView view) {
        views.remove(view.id());
    }

    public ComponentView root() {
        return root;
    }

    public ComponentView view(long id) {
        return views.get(id);
    }

    public int size() {
        return views.size();
    }

    /// The outer HTML of a view: host open tag, template HTML with child markers replaced recursively, close tag.
    public String assemble(ComponentView view) {
        final var out = new StringBuilder();
        if (view.hostOpenTag() != null) out.append(view.hostOpenTag());
        substitute(view.templateHtml(), out);
        if (view.hostTagName() != null) out.append("</").append(view.hostTagName()).append('>');
        return out.toString();
    }

    private void substitute(String html, StringBuilder out) {
        var from = 0;
        while (true) {
            final var start = html.indexOf(ComponentView.MARKER, from);
            if (start < 0) {
                out.append(html, from, html.length());
                return;
            }
            final var end = html.indexOf(ComponentView.MARKER, start + 1);
            out.append(html, from, start);
            final var child = views.get(Long.parseLong(html.substring(start + 1, end)));
            if (child != null && !child.isDestroyed()) {
                out.append(assemble(child));
            }
            from = end + 1;
        }
    }
}
