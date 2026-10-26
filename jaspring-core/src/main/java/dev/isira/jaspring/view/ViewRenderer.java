package dev.isira.jaspring.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.isira.jaspring.cd.lifecycle.AfterContentChecked;
import dev.isira.jaspring.cd.lifecycle.AfterContentInit;
import dev.isira.jaspring.cd.lifecycle.AfterViewChecked;
import dev.isira.jaspring.cd.lifecycle.AfterViewInit;
import dev.isira.jaspring.cd.lifecycle.DoCheck;
import dev.isira.jaspring.cd.lifecycle.OnChanges;
import dev.isira.jaspring.cd.lifecycle.OnDestroy;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.cd.lifecycle.SimpleChange;
import dev.isira.jaspring.cd.lifecycle.SimpleChanges;
import dev.isira.jaspring.component.ComponentDef;
import dev.isira.jaspring.component.ComponentDefs;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.EvaluationContext;
import dev.isira.jaspring.template.eval.ExpressionEvaluator;
import dev.isira.jaspring.template.eval.Scope;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.render.HtmlWriter;
import dev.isira.jaspring.template.render.RenderHost;
import dev.isira.jaspring.template.render.TemplateRenderer;
import dev.isira.jaspring.template.selector.CssSelector;

/// Renders component views: creates and reuses child views by key, applies inputs, runs the lifecycle hooks in
/// Angular's order, subscribes outputs, projects content and records the pieces `ViewTree.assemble` needs.
public final class ViewRenderer {

    private final ComponentRegistry registry;
    private final TemplateRenderer renderer;
    private final boolean liveEvents;

    public ViewRenderer(ComponentRegistry registry, DomSchema schema, boolean liveEvents) {
        this.registry = registry;
        this.renderer = new TemplateRenderer(schema);
        this.liveEvents = liveEvents;
    }

    /// Creates the root view of a page, applies the initial inputs and renders the whole tree.
    public ComponentView createRoot(ViewTree tree, Class<?> type, Map<String, Object> inputs) {
        final var def = registry.component(type);
        final var view = tree.create(def, instantiate(def), null);
        final var selector = def.selectors().get(0);
        final var tagName = selector.element() == null ? "div" : selector.element();
        final var hostAttributes = new LinkedHashMap<String, String>();
        if (!def.styles().isEmpty()) hostAttributes.put("_nghost-" + def.scopeId(), "");
        view.host(tagName, hostOpenTag(tagName, hostAttributes, List.of(), view));
        final var changes = applyInputs(view, inputs);
        runPreRenderHooks(view, changes);
        renderView(tree, view, null);
        return view;
    }

    /// Re-renders one view and, through its template, every child component it contains.
    public void refresh(ViewTree tree, ComponentView view) {
        if (view.parent() == null) runPreRenderHooks(view, new SimpleChanges());
        renderView(tree, view, null);
    }

    private void renderView(ViewTree tree, ComponentView view, ProjectionSlots slots) {
        final var visited = new HashSet<String>();
        view.clearHandlers();
        final var out = new HtmlWriter();
        final var context = EvaluationContext.of(view.instance()).withPipes(registry);
        renderer.render(registry.template(view.def().type()).parsed().nodes(), context, out, new ViewHost(tree, view, visited, slots));
        view.templateHtml(out.toString());
        view.clearDirty();
        destroyUnvisitedChildren(tree, view, visited);
        if (!view.isViewInitialized()) {
            view.markViewInitialized();
            if (view.instance() instanceof AfterViewInit hook) hook.ngAfterViewInit();
        }
        if (view.instance() instanceof AfterViewChecked hook) hook.ngAfterViewChecked();
    }

    private static void runPreRenderHooks(ComponentView view, SimpleChanges changes) {
        final var instance = view.instance();
        if (!changes.isEmpty() && instance instanceof OnChanges hook) hook.ngOnChanges(changes);
        if (!view.isInitialized()) {
            view.markInitialized();
            if (instance instanceof OnInit hook) hook.ngOnInit();
        }
        if (instance instanceof DoCheck hook) hook.ngDoCheck();
        if (!view.isContentInitialized()) {
            view.markContentInitialized();
            if (instance instanceof AfterContentInit hook) hook.ngAfterContentInit();
        }
        if (instance instanceof AfterContentChecked hook) hook.ngAfterContentChecked();
    }

    public void destroy(ViewTree tree, ComponentView view) {
        if (view.isDestroyed()) return;
        for (final var child : List.copyOf(view.children().values())) {
            destroy(tree, child);
        }
        view.children().clear();
        view.markDestroyed();
        view.subscriptions().forEach(dev.isira.jaspring.signals.Subscription::unsubscribe);
        if (view.instance() instanceof OnDestroy hook) hook.ngOnDestroy();
        registry.factory().destroy(view.instance());
        tree.remove(view);
    }

    private Object instantiate(ComponentDef def) {
        final var instance = registry.factory().create(def.type());
        ComponentDefs.bindSignalNames(instance);
        return instance;
    }

    private void destroyUnvisitedChildren(ViewTree tree, ComponentView view, Set<String> visited) {
        for (final var entry : List.copyOf(view.children().entrySet())) {
            if (!visited.contains(entry.getKey())) {
                view.children().remove(entry.getKey());
                destroy(tree, entry.getValue());
            }
        }
    }

    /// Applies new input values, collecting `SimpleChanges` for `ngOnChanges`. Unchanged values are skipped.
    private SimpleChanges applyInputs(ComponentView view, Map<String, Object> values) {
        final var changes = new SimpleChanges();
        final var previous = view.inputs();
        values.forEach((name, value) -> {
            final var input = view.def().inputs().get(name);
            if (input == null) return;
            final var first = !previous.containsKey(name);
            final var old = previous.get(name);
            if (!first && java.util.Objects.equals(old, value)) return;
            input.apply(view.instance(), value);
            previous.put(name, value);
            changes.put(name, new SimpleChange(first ? null : old, value, first));
        });
        return changes;
    }

    private static String hostOpenTag(String tagName, Map<String, String> attributes, List<String> eventMarkers, ComponentView view) {
        final var writer = new HtmlWriter();
        writer.openTag(tagName);
        attributes.forEach(writer::attribute);
        writer.attribute("data-jsp-v", Long.toString(view.id()));
        if (!eventMarkers.isEmpty()) writer.attribute("data-jsp-on", String.join(" ", eventMarkers));
        writer.closeStartTag();
        return writer.toString();
    }

    /// The render host of one view: registers its handlers and turns component elements into child views.
    private final class ViewHost implements RenderHost {

        private final ViewTree tree;
        private final ComponentView view;
        private final Set<String> visited;
        private final ProjectionSlots projected;

        ViewHost(ViewTree tree, ComponentView view, Set<String> visited) {
            this(tree, view, visited, null);
        }

        ViewHost(ViewTree tree, ComponentView view, Set<String> visited, ProjectionSlots projected) {
            this.tree = tree;
            this.view = view;
            this.visited = visited;
            this.projected = projected;
        }

        @Override
        public String registerHandler(TemplateNode.BoundEvent event, Scope scope) {
            return liveEvents ? view.registerHandler(new HandlerBinding(event, scope)) : null;
        }

        @Override
        public String contentAttribute() {
            return view.def().styles().isEmpty() ? null : "_ngcontent-" + view.def().scopeId();
        }

        @Override
        public ComponentDef matchComponent(String tagName, Map<String, String> attributes, Set<String> boundNames) {
            return registry.match(tagName, attributes, boundNames);
        }

        @Override
        public RenderedComponent renderComponent(ComponentOccurrence occurrence) {
            final var key = occurrence.viewKey();
            visited.add(key);
            var child = view.children().get(key);
            final var created = child == null;
            if (created) {
                child = tree.create(occurrence.def(), instantiate(occurrence.def()), view);
                view.children().put(key, child);
                subscribeOutputs(child, occurrence);
            }
            final var tagName = TemplateRenderer.localName(occurrence.element().name());
            final var hostAttributes = new LinkedHashMap<>(occurrence.hostAttributes());
            if (!child.def().styles().isEmpty()) hostAttributes.put("_nghost-" + child.def().scopeId(), "");
            if (contentAttribute() != null) hostAttributes.put(contentAttribute(), "");
            child.host(tagName, hostOpenTag(tagName, hostAttributes, occurrence.nativeEventMarkers(), child));

            child.outputScope(occurrence.scope());
            final var changes = applyInputs(child, occurrence.inputs());
            runPreRenderHooks(child, changes);
            renderView(tree, child, new ProjectionSlots(registry.template(child.def().type()).parsed().ngContentSelectors(),
                    occurrence.projectedNodes(), occurrence.projection()));
            return new RenderedComponent(child.marker(), child.instance());
        }

        /// `(saved)="onSaved($event)"` on a component element runs the parent's handler when the child emits.
        private void subscribeOutputs(ComponentView child, ComponentOccurrence occurrence) {
            for (final var output : occurrence.outputs()) {
                final var def = child.def().outputs().get(output.name());
                child.subscriptions().add(def.subscribe(child.instance(), value -> {
                    final var scope = child.outputScope().with("$event", value);
                    final var context = EvaluationContext.of(view.instance()).withPipes(registry).withScope(scope);
                    Signals.untracked(() -> {
                        // A two-way binding's "handler" is the target expression itself.
                        if (output.type() == dev.isira.jaspring.template.ast.EventType.TWO_WAY) {
                            ExpressionEvaluator.assignTo(output.handler(), value, context);
                        } else {
                            ExpressionEvaluator.evaluate(output.handler(), context);
                        }
                    });
                }));
            }
        }

        @Override
        public boolean renderContent(String selector, HtmlWriter out) {
            if (projected == null) return false;
            final var nodes = projected.nodesFor(selector);
            if (nodes.isEmpty()) return false;
            projected.projection().render(nodes, out);
            return true;
        }
    }

    /// Partitions the nodes projected into a child among its `<ng-content select>` slots, Angular style:
    /// each node goes to the first selector it matches, text to the wildcard slot, `ngProjectAs` overrides.
    static final class ProjectionSlots {

        private final List<String> selectors;
        private final Map<String, List<TemplateNode>> slots = new LinkedHashMap<>();
        private final RenderHost.Projection projection;

        ProjectionSlots(List<String> selectors, List<TemplateNode> nodes, RenderHost.Projection projection) {
            this.selectors = selectors;
            this.projection = projection;
            final var parsed = new ArrayList<List<CssSelector>>();
            for (final var selector : selectors) {
                parsed.add(selector.equals("*") ? null : CssSelector.parse(selector));
            }
            for (final var node : nodes) {
                final var slot = slotFor(node, parsed);
                if (slot != null) slots.computeIfAbsent(slot, s -> new ArrayList<>()).add(node);
            }
        }

        private String slotFor(TemplateNode node, List<List<CssSelector>> parsed) {
            if (node instanceof TemplateNode.Element element) {
                final var attributes = new LinkedHashMap<String, String>();
                element.attributes().forEach(a -> attributes.put(TemplateRenderer.localName(a.name()), a.value()));
                final var bound = new HashSet<String>();
                element.inputs().forEach(i -> bound.add(i.name()));
                final var projectAs = attributes.get("ngProjectAs");
                final var candidate = projectAs != null
                        ? candidateFromSelector(CssSelector.parse(projectAs).get(0))
                        : CssSelector.forElement(TemplateRenderer.localName(element.name()), attributes, bound);
                for (var i = 0; i < selectors.size(); i++) {
                    final var slot = parsed.get(i);
                    if (slot != null && slot.stream().anyMatch(s -> s.matches(candidate))) return selectors.get(i);
                }
            }
            return selectors.contains("*") ? "*" : null;
        }

        // ngProjectAs names a selector; matching treats the element as if it were exactly that selector.
        private static CssSelector candidateFromSelector(CssSelector selector) {
            final var attributes = new LinkedHashMap<String, String>();
            final var text = selector.toString();
            final var element = selector.element() == null ? "div" : selector.element();
            final var matcher = java.util.regex.Pattern.compile("\\[([^\\]=]+)(?:=([^\\]]*))?\\]|\\.([-\\w]+)").matcher(text);
            final var classes = new StringBuilder();
            while (matcher.find()) {
                if (matcher.group(1) != null) attributes.put(matcher.group(1), matcher.group(2) == null ? "" : matcher.group(2));
                else classes.append(' ').append(matcher.group(3));
            }
            if (!classes.isEmpty()) attributes.put("class", classes.toString().trim());
            return CssSelector.forElement(element, attributes, Set.of());
        }

        List<TemplateNode> nodesFor(String selector) {
            return slots.getOrDefault(selector, List.of());
        }

        RenderHost.Projection projection() {
            return projection;
        }
    }
}
