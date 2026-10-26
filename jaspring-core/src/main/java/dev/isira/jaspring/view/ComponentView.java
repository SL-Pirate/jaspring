package dev.isira.jaspring.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.isira.jaspring.component.ComponentDef;
import dev.isira.jaspring.signals.Subscription;

/// One component instance in a page: its definition, instance, children keyed by template position and track value,
/// the last applied inputs, the rendered pieces and the event handlers of the last render.
///
/// The parent renders the host open tag; the view renders `templateHtml`, in which child components appear as
/// `\u0000<childId>\u0000` markers that `ViewTree.assemble` replaces with the children's HTML.
public final class ComponentView {

    static final char MARKER = '\u0000';

    private static final dev.isira.jaspring.context.CurrentContext<ComponentView> CURRENT = dev.isira.jaspring.context.CurrentContext.threadLocal();

    private final long id;
    private final ComponentDef def;
    private Object instance;
    private final ComponentView parent;
    private final ViewConsumerNode consumer = new ViewConsumerNode(this);
    private dev.isira.jaspring.cd.ChangeDetector changeDetector;
    private Object projectionSlots;
    private boolean attached = true;
    private boolean hostChanged;
    private final Map<String, ComponentView> children = new LinkedHashMap<>();
    private final Map<String, Object> inputs = new HashMap<>();
    private final Map<Integer, HandlerBinding> handlers = new HashMap<>();
    private final Map<String, Object> directives = new LinkedHashMap<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private int handlerSeq;
    private String hostTagName;
    private String hostOpenTag;
    private String templateHtml = "";
    private boolean initialized;
    private boolean viewInitialized;
    private boolean contentInitialized;
    private boolean destroyed;
    private boolean dirty = true;
    private boolean htmlChanged;
    private dev.isira.jaspring.template.eval.Scope outputScope = dev.isira.jaspring.template.eval.Scope.root();

    ComponentView(long id, ComponentDef def, ComponentView parent) {
        this.id = id;
        this.def = def;
        this.parent = parent;
    }

    /// The view whose component is being constructed or whose hook runs on this thread, if any.
    public static ComponentView current() {
        return CURRENT.get();
    }

    static <T> T runAs(ComponentView view, java.util.function.Supplier<T> body) {
        return CURRENT.runWith(view, body);
    }

    void attach(Object instance) {
        this.instance = instance;
    }

    public ViewConsumerNode consumer() {
        return consumer;
    }

    public dev.isira.jaspring.cd.ChangeDetector changeDetector() {
        return changeDetector;
    }

    void changeDetector(dev.isira.jaspring.cd.ChangeDetector detector) {
        this.changeDetector = detector;
    }

    Object projectionSlots() {
        return projectionSlots;
    }

    void projectionSlots(Object slots) {
        this.projectionSlots = slots;
    }

    public boolean isAttached() {
        return attached;
    }

    public void setAttached(boolean attached) {
        this.attached = attached;
    }

    public boolean hostChanged() {
        return hostChanged;
    }

    public void clearHostChanged() {
        hostChanged = false;
    }

    public long id() {
        return id;
    }

    public ComponentDef def() {
        return def;
    }

    public Object instance() {
        return instance;
    }

    public ComponentView parent() {
        return parent;
    }

    public Map<String, ComponentView> children() {
        return children;
    }

    Map<String, Object> inputs() {
        return inputs;
    }

    public String marker() {
        return MARKER + Long.toString(id) + MARKER;
    }

    // ---- handlers ----

    String registerHandler(HandlerBinding binding) {
        final var seq = ++handlerSeq;
        handlers.put(seq, binding);
        return id + "." + seq;
    }

    void clearHandlers() {
        handlers.clear();
        handlerSeq = 0;
    }

    public HandlerBinding handler(int seq) {
        return handlers.get(seq);
    }

    List<Subscription> subscriptions() {
        return subscriptions;
    }

    /// Directive instances of this view's template, keyed by element occurrence and directive class.
    Map<String, Object> directives() {
        return directives;
    }

    /// The parent scope the host element was last rendered in; output handlers evaluate in it.
    dev.isira.jaspring.template.eval.Scope outputScope() {
        return outputScope;
    }

    void outputScope(dev.isira.jaspring.template.eval.Scope scope) {
        outputScope = scope;
    }

    // ---- rendered pieces ----

    void host(String tagName, String openTag) {
        if (!openTag.equals(hostOpenTag)) hostChanged = true;
        this.hostTagName = tagName;
        this.hostOpenTag = openTag;
    }

    public String hostOpenTag() {
        return hostOpenTag;
    }

    void templateHtml(String html) {
        htmlChanged = !html.equals(templateHtml);
        templateHtml = html;
    }

    public String templateHtml() {
        return templateHtml;
    }

    String hostTagName() {
        return hostTagName;
    }

    // ---- lifecycle flags ----

    boolean isInitialized() {
        return initialized;
    }

    void markInitialized() {
        initialized = true;
    }

    boolean isContentInitialized() {
        return contentInitialized;
    }

    void markContentInitialized() {
        contentInitialized = true;
    }

    boolean isViewInitialized() {
        return viewInitialized;
    }

    void markViewInitialized() {
        viewInitialized = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    void markDestroyed() {
        destroyed = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    void clearDirty() {
        dirty = false;
    }

    public boolean htmlChanged() {
        return htmlChanged;
    }

    public void clearHtmlChanged() {
        htmlChanged = false;
    }

    @Override
    public String toString() {
        return "ComponentView#" + id + "(" + def.name() + ")";
    }
}
