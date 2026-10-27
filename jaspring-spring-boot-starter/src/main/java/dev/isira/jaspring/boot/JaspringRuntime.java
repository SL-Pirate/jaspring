package dev.isira.jaspring.boot;

import java.util.Optional;

import org.springframework.context.i18n.LocaleContextHolder;

import dev.isira.jaspring.boot.browser.BrowserAssets;
import dev.isira.jaspring.boot.live.LiveSessions;
import dev.isira.jaspring.browser.ClosureManifest;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.live.BrowserResult;
import dev.isira.jaspring.live.EventEnvelope;
import dev.isira.jaspring.live.LivePage;
import dev.isira.jaspring.live.LivePageRegistry;
import dev.isira.jaspring.live.PageSpec;
import dev.isira.jaspring.live.PatchSet;
import dev.isira.jaspring.live.PatchSink;
import dev.isira.jaspring.pipe.LocaleContext;
import dev.isira.jaspring.template.parse.DomSchema;

/// The entry point the view and the transports use: create and render pages, dispatch events.
public final class JaspringRuntime {

    private final ComponentRegistry registry;
    private final DomSchema schema;
    private final LivePageRegistry pages;
    private final LayoutRenderer layout;
    private final JaspringProperties properties;
    private final LiveSessions sessions;
    private final ClosureManifest closures;

    /// `sessions` is null when the live transport is off; `closures` is `EMPTY` without a WebAssembly build.
    public JaspringRuntime(ComponentRegistry registry, DomSchema schema, LivePageRegistry pages, LayoutRenderer layout, JaspringProperties properties,
            LiveSessions sessions, ClosureManifest closures) {
        this.registry = registry;
        this.schema = schema;
        this.pages = pages;
        this.layout = layout;
        this.properties = properties;
        this.sessions = sessions;
        this.closures = closures;
    }

    public ClosureManifest closures() {
        return closures;
    }

    public JaspringProperties properties() {
        return properties;
    }

    public LivePageRegistry pages() {
        return pages;
    }

    /// Renders a full HTML document. With a live transport the page stays registered for the session; with
    /// transport `off` it is rendered and discarded.
    public String renderDocument(PageSpec spec, String sessionId, PatchSink initialSink) {
        final var live = properties.getLive().getTransport() != JaspringProperties.Transport.OFF;
        final var page = new LivePage(LivePageRegistry.newPageId(), sessionId, spec, registry, schema, properties.getChangeDetection(), live,
                initialSink == null ? PatchSink.NONE : initialSink, closures);
        // The queue is the sink from the start, so pushes between the response and the first client contact survive.
        final var state = live && initialSink == null && sessions != null ? sessions.state(page) : null;
        final var def = registry.component(spec.rootComponent());
        final var rootTag = def.selectors().get(0).element() == null ? "div" : def.selectors().get(0).element();
        try {
            final var html = LocaleContext.runWith(LocaleContextHolder.getLocale(), () -> page.renderInitial(properties.getStabilityTimeout()));
            if (live) {
                pages.register(page);
                final var pending = !page.zone().isStable() || (state != null && !state.queue().isEmpty());
                final var rendered = page.tree().views().stream().<Class<?>>map(view -> view.def().type()).toList();
                return layout.render(rootTag, html, page.id(), pending, BrowserAssets.forPage(closures, rendered));
            }
            return layout.render(rootTag, html, null);
        } finally {
            if (!live) page.destroy();
        }
    }

    public Optional<LivePage> find(String pageId, String sessionId) {
        return pages.find(pageId, sessionId);
    }

    public PatchSet handleEvent(LivePage page, EventEnvelope envelope) {
        return LocaleContext.runWith(LocaleContextHolder.getLocale(), () -> page.handleEvent(envelope));
    }

    public PatchSet handleResult(LivePage page, BrowserResult result) {
        return LocaleContext.runWith(LocaleContextHolder.getLocale(), () -> page.handleResult(result));
    }
}
