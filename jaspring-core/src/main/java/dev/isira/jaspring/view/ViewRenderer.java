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
import dev.isira.jaspring.component.ChangeDetectionStrategy;
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

    private final dev.isira.jaspring.browser.ClosureManifest closures;

    public ViewRenderer(ComponentRegistry registry, DomSchema schema, boolean liveEvents) {
        this(registry, schema, liveEvents, dev.isira.jaspring.browser.ClosureManifest.EMPTY);
    }

    /// `closures` marks handlers that run browser closures, so the shim can warm the WebAssembly runtime early.
    public ViewRenderer(ComponentRegistry registry, DomSchema schema, boolean liveEvents, dev.isira.jaspring.browser.ClosureManifest closures) {
        this.closures = closures;
        this.registry = registry;
        this.renderer = new TemplateRenderer(schema);
        this.liveEvents = liveEvents;
    }

    public ComponentRegistry registry() {
        return registry;
    }

    /// Creates the root view of a page, applies the initial inputs and renders the whole tree.
    public ComponentView createRoot(ViewTree tree, Class<?> type, Map<String, Object> inputs) {
        return createRoot(tree, type, inputs, null);
    }

    public ComponentView createRoot(ViewTree tree, Class<?> type, Map<String, Object> inputs, dev.isira.jaspring.cd.ChangeDetector detector) {
        final var def = registry.component(type);
        final var view = tree.create(def, null);
        view.changeDetector(detector);
        instantiate(view);
        final var selector = def.selectors().get(0);
        final var tagName = selector.element() == null ? "div" : selector.element();
        final var hostAttributes = new LinkedHashMap<String, String>();
        if (!def.styles().isEmpty()) hostAttributes.put("_nghost-" + def.scopeId(), "");
        view.host(tagName, hostOpenTag(tagName, hostAttributes, List.of(), view));
        final var changes = applyInputs(view, inputs);
        runPreRenderHooks(view, changes);
        renderView(tree, view, null, detector);
        return view;
    }

    /// Re-renders one view and, through its template, every child component it contains.
    public void refresh(ViewTree tree, ComponentView view) {
        refresh(tree, view, view.changeDetector());
    }

    /// Re-renders one view. With a detector, child components follow its Default/OnPush decisions instead of always re-rendering.
    public void refresh(ViewTree tree, ComponentView view, dev.isira.jaspring.cd.ChangeDetector detector) {
        if (view.parent() == null) runPreRenderHooks(view, new SimpleChanges());
        else if (view.instance() instanceof DoCheck hook) untracked(view, hook::ngDoCheck);
        renderView(tree, view, (ProjectionSlots) view.projectionSlots(), detector);
    }

    private final Map<ComponentView, Map<String, Map<String, Object>>> directiveInputs = new java.util.WeakHashMap<>();

    private Map<String, Map<String, Object>> directiveInputsOf(ComponentView view) {
        return directiveInputs.computeIfAbsent(view, v -> new java.util.HashMap<>());
    }

    private void renderView(ViewTree tree, ComponentView view, ProjectionSlots slots, dev.isira.jaspring.cd.ChangeDetector detector) {
        final var visited = new HashSet<String>();
        view.clearHandlers();
        view.projectionSlots(slots);
        view.changeDetector(detector);
        final var out = new HtmlWriter();
        final var context = EvaluationContext.of(view.instance()).withPipes(registry);
        final var host = new ViewHost(tree, view, visited, slots, detector);
        // Signals read by the template link to the view's consumer; a later write marks the view dirty.
        view.consumer().clearDirty();
        dev.isira.jaspring.signals.internal.Graph.computeAs(view.consumer(), () -> {
            renderer.render(registry.template(view.def().type()).parsed().nodes(), context, out, host);
            return null;
        });
        view.templateHtml(out.toString());
        view.clearDirty();
        host.releaseUnvisitedDirectives();
        destroyUnvisitedChildren(tree, view, visited);
        if (!view.isViewInitialized()) {
            view.markViewInitialized();
            if (view.instance() instanceof AfterViewInit hook) untracked(view, hook::ngAfterViewInit);
        }
        if (view.instance() instanceof AfterViewChecked hook) untracked(view, hook::ngAfterViewChecked);
    }

    /// Hooks run with the view bound (for `ChangeDetectorRef.current()`) and without reactive tracking, like Angular.
    static void untracked(ComponentView view, Runnable hook) {
        ComponentView.runAs(view, () -> Signals.untracked(() -> {
            hook.run();
            return null;
        }));
    }

    private static void runPreRenderHooks(ComponentView view, SimpleChanges changes) {
        final var instance = view.instance();
        if (!changes.isEmpty() && instance instanceof OnChanges hook) untracked(view, () -> hook.ngOnChanges(changes));
        if (!view.isInitialized()) {
            view.markInitialized();
            if (instance instanceof OnInit hook) untracked(view, hook::ngOnInit);
        }
        if (instance instanceof DoCheck hook) untracked(view, hook::ngDoCheck);
        if (!view.isContentInitialized()) {
            view.markContentInitialized();
            if (instance instanceof AfterContentInit hook) untracked(view, hook::ngAfterContentInit);
        }
        if (instance instanceof AfterContentChecked hook) untracked(view, hook::ngAfterContentChecked);
    }

    public void destroy(ViewTree tree, ComponentView view) {
        if (view.isDestroyed()) return;
        for (final var child : List.copyOf(view.children().values())) {
            destroy(tree, child);
        }
        view.children().clear();
        view.markDestroyed();
        for (final var directive : view.directives().values()) {
            if (directive instanceof OnDestroy hook) hook.ngOnDestroy();
            registry.factory().destroy(directive);
        }
        view.directives().clear();
        view.subscriptions().forEach(dev.isira.jaspring.signals.Subscription::unsubscribe);
        dev.isira.jaspring.signals.internal.Graph.consumerDestroy(view.consumer());
        if (view.instance() instanceof OnDestroy hook) untracked(view, hook::ngOnDestroy);
        registry.factory().destroy(view.instance());
        tree.remove(view);
    }

    private void instantiate(ComponentView view) {
        ComponentView.runAs(view, () -> Signals.untracked(() -> {
            final var instance = registry.factory().create(view.def().type());
            ComponentDefs.bindSignalNames(instance);
            view.attach(instance);
            return null;
        }));
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
        private final Set<String> visitedDirectives = new HashSet<>();
        private final Map<String, Map<String, Object>> directiveInputs;
        private final ProjectionSlots projected;
        private final dev.isira.jaspring.cd.ChangeDetector detector;

        ViewHost(ViewTree tree, ComponentView view, Set<String> visited, ProjectionSlots projected, dev.isira.jaspring.cd.ChangeDetector detector) {
            this.tree = tree;
            this.view = view;
            this.visited = visited;
            this.projected = projected;
            this.detector = detector;
            this.directiveInputs = directiveInputsOf(view);
        }

        /// Destroys directive instances whose element did not render this time.
        void releaseUnvisitedDirectives() {
            for (final var entry : List.copyOf(view.directives().entrySet())) {
                if (visitedDirectives.contains(entry.getKey())) continue;
                view.directives().remove(entry.getKey());
                directiveInputs.remove(entry.getKey());
                if (entry.getValue() instanceof OnDestroy hook) hook.ngOnDestroy();
                registry.factory().destroy(entry.getValue());
            }
        }

        @Override
        public String registerHandler(TemplateNode.BoundEvent event, Scope scope) {
            return liveEvents ? view.registerHandler(new HandlerBinding(event, scope)) : null;
        }

        @Override
        public boolean handlerUsesBrowser(dev.isira.jaspring.template.expr.Expr handler) {
            final var methods = closures.eventMethods(view.def().type());
            return !methods.isEmpty() && dev.isira.jaspring.template.render.HandlerFlags.calledMethods(handler).stream().anyMatch(methods::contains);
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
        public List<dev.isira.jaspring.component.DirectiveDef> matchDirectives(String tagName, Map<String, String> attributes, Set<String> boundNames) {
            return registry.matchDirectives(tagName, attributes, boundNames);
        }

        @Override
        public Object applyDirective(dev.isira.jaspring.component.DirectiveDef def, String elementKey, Map<String, Object> inputs) {
            visitedDirectives.add(elementKey);
            var instance = view.directives().get(elementKey);
            final var created = instance == null;
            if (created) {
                instance = registry.factory().create(def.type());
                ComponentDefs.bindSignalNames(instance);
                view.directives().put(elementKey, instance);
            }
            final var previous = directiveInputs.computeIfAbsent(elementKey, k -> new java.util.HashMap<>());
            final var changes = new SimpleChanges();
            for (final var entry : inputs.entrySet()) {
                final var input = def.inputs().get(entry.getKey());
                final var first = !previous.containsKey(entry.getKey());
                final var old = previous.get(entry.getKey());
                if (!first && java.util.Objects.equals(old, entry.getValue())) continue;
                input.apply(instance, entry.getValue());
                previous.put(entry.getKey(), entry.getValue());
                changes.put(entry.getKey(), new SimpleChange(first ? null : old, entry.getValue(), first));
            }
            if (!changes.isEmpty() && instance instanceof OnChanges hook) hook.ngOnChanges(changes);
            if (created && instance instanceof OnInit hook) hook.ngOnInit();
            if (instance instanceof DoCheck hook) hook.ngDoCheck();
            return instance;
        }

        @Override
        public RenderedComponent renderComponent(ComponentOccurrence occurrence) {
            final var key = occurrence.viewKey();
            visited.add(key);
            var child = view.children().get(key);
            final var created = child == null;
            if (created) {
                child = tree.create(occurrence.def(), view);
                child.changeDetector(detector);
                instantiate(child);
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
            if (!changes.isEmpty()) child.markDirty();
            final var slots = new ProjectionSlots(registry.template(child.def().type()).parsed().ngContentSelectors(),
                    occurrence.projectedNodes(), occurrence.projection());
            child.projectionSlots(slots);
            // Angular checks children of a refreshed view in Global mode: Default always, OnPush only when dirty.
            final var refresh = detector == null || created || child.def().changeDetection() == ChangeDetectionStrategy.DEFAULT || child.isDirty();
            if (refresh && child.isAttached()) {
                runPreRenderHooks(child, changes);
                renderView(tree, child, slots, detector);
            } else {
                runPreRenderHooks(child, changes);
                for (final var grandchild : List.copyOf(child.children().values())) {
                    detector.check(grandchild, false);
                }
            }
            return new RenderedComponent(child.marker(), child.instance());
        }

        /// `(saved)="onSaved($event)"` on a component element runs the parent's handler when the child emits.
        private void subscribeOutputs(ComponentView child, ComponentOccurrence occurrence) {
            for (final var output : occurrence.outputs()) {
                final var def = child.def().outputs().get(output.name());
                child.subscriptions().add(def.subscribe(child.instance(), value -> {
                    final var scope = child.outputScope().with("$event", value);
                    final var context = EvaluationContext.of(view.instance()).withPipes(registry).withScope(scope);
                    for (var current = view; current != null; current = current.parent()) {
                        current.markDirty();
                    }
                    ComponentView.runAs(view, () -> {
                        Signals.untracked(() -> {
                            // A two-way binding's "handler" is the target expression itself.
                            if (output.type() == dev.isira.jaspring.template.ast.EventType.TWO_WAY) {
                                ExpressionEvaluator.assignTo(output.handler(), value, context);
                            } else {
                                ExpressionEvaluator.evaluate(output.handler(), context);
                            }
                        });
                        return null;
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
