package dev.isira.jaspring.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.cd.lifecycle.OnChanges;
import dev.isira.jaspring.cd.lifecycle.OnDestroy;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.cd.lifecycle.SimpleChanges;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Directive;
import dev.isira.jaspring.component.HostBinding;
import dev.isira.jaspring.component.HostListener;
import dev.isira.jaspring.component.Input;

class DirectiveTest {

    static final List<String> LOG = new ArrayList<>();

    @Directive(selector = "[appHighlight]")
    public static class Highlight implements OnChanges, OnInit, OnDestroy {
        @Input public String appHighlight = "yellow";
        @Input public boolean strong;
        public int clicks;

        @HostBinding("style.background") public String background() { return appHighlight; }
        @HostBinding("class.hl") public boolean hl = true;
        @HostBinding("class.strong") public boolean isStrong() { return strong; }
        @HostBinding("attr.role") public String role = "note";
        @HostBinding("title") public String title() { return "highlighted " + appHighlight; }

        @HostListener(value = "click", args = {"$event"})
        public void onClick(Object event) {
            clicks++;
        }

        @Override public void ngOnChanges(SimpleChanges changes) { LOG.add("changes " + changes.asMap().keySet()); }
        @Override public void ngOnInit() { LOG.add("init"); }
        @Override public void ngOnDestroy() { LOG.add("destroy"); }
    }

    @Component(selector = "app-host", template = "<p appHighlight=\"pink\" [strong]=\"on\">a</p>@if (show) {<span [appHighlight]=\"color\">b</span>}")
    public static class HostComponent {
        public boolean on = true;
        public boolean show = true;
        public String color = "lime";
    }

    @Test
    void directivesApplyInputsHostBindingsAndListeners() {
        LOG.clear();
        final var page = new ViewFixture.Page(true, Highlight.class, HostComponent.class);

        final var html = page.render(HostComponent.class);
        assertEquals("<app-host data-jsp-v=\"1\"><p appHighlight=\"pink\" role=\"note\" title=\"highlighted pink\" class=\"hl strong\" style=\"background: pink\" data-jsp-on=\"click:1.1\">a</p>"
                + "<span role=\"note\" title=\"highlighted lime\" class=\"hl\" style=\"background: lime\" data-jsp-on=\"click:1.2\">b</span></app-host>", html);
        assertEquals(List.of("changes [appHighlight, strong]", "init", "changes [appHighlight]", "init"), LOG);

        final var handler = page.root.handler(1);
        assertEquals("click", handler.event().name());
        assertTrue(handler.scope().has("$dir"));
    }

    @Test
    void directiveInstancesPersistAcrossRendersAndDieWithTheirElement() {
        LOG.clear();
        final var page = new ViewFixture.Page(false, Highlight.class, HostComponent.class);
        page.render(HostComponent.class);
        final var instances = List.copyOf(page.root.directives().values());
        LOG.clear();

        final var component = (HostComponent) page.root.instance();
        component.color = "red";
        component.show = false;
        page.refresh();

        assertSame(instances.get(0), page.root.directives().values().iterator().next());
        assertEquals(1, page.root.directives().size());
        assertEquals(List.of("destroy"), LOG);
    }
}
