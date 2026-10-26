package dev.isira.jaspring.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.cd.lifecycle.AfterContentChecked;
import dev.isira.jaspring.cd.lifecycle.AfterContentInit;
import dev.isira.jaspring.cd.lifecycle.AfterViewChecked;
import dev.isira.jaspring.cd.lifecycle.AfterViewInit;
import dev.isira.jaspring.cd.lifecycle.DoCheck;
import dev.isira.jaspring.cd.lifecycle.OnChanges;
import dev.isira.jaspring.cd.lifecycle.OnDestroy;
import dev.isira.jaspring.cd.lifecycle.OnInit;
import dev.isira.jaspring.cd.lifecycle.SimpleChanges;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Input;
import dev.isira.jaspring.signals.InputSignal;
import dev.isira.jaspring.signals.ModelSignal;
import dev.isira.jaspring.signals.OutputEmitter;
import dev.isira.jaspring.signals.Signals;

class ViewRendererTest {

    static final List<String> LOG = new ArrayList<>();

    @Component(selector = "app-card", template = "<h2>{{ title }}</h2><ng-content select=\"[slot=header]\"></ng-content><div class=\"body\"><ng-content>fallback</ng-content></div>{{ size() }}")
    public static class Card implements OnChanges, OnInit, DoCheck, AfterContentInit, AfterContentChecked, AfterViewInit, AfterViewChecked, OnDestroy {
        @Input public String title = "untitled";
        public InputSignal<Integer> size = Signals.input(1);
        public OutputEmitter<String> saved = Signals.output();

        public Card() {
            LOG.add("constructor");
        }

        @Override public void ngOnChanges(SimpleChanges changes) { LOG.add("ngOnChanges " + changes.asMap().keySet()); }
        @Override public void ngOnInit() { LOG.add("ngOnInit"); }
        @Override public void ngDoCheck() { LOG.add("ngDoCheck"); }
        @Override public void ngAfterContentInit() { LOG.add("ngAfterContentInit"); }
        @Override public void ngAfterContentChecked() { LOG.add("ngAfterContentChecked"); }
        @Override public void ngAfterViewInit() { LOG.add("ngAfterViewInit"); }
        @Override public void ngAfterViewChecked() { LOG.add("ngAfterViewChecked"); }
        @Override public void ngOnDestroy() { LOG.add("ngOnDestroy " + title); }
    }

    @Component(selector = "app-parent", template = """
            <app-card [title]="heading" [size]="n" class="c" [class.on]="on" (saved)="last = $event" (click)="noop()" #card><b slot="header">H</b>body {{ heading }}</app-card>{{ card.title }}
            @for (name of names; track name) {<app-card [title]="name" (saved)="last = name + ':' + $event"></app-card>}
            """)
    public static class Parent {
        public String heading = "Hello";
        public int n = 3;
        public boolean on = true;
        public String last = "";
        public List<String> names = new ArrayList<>(List.of("a", "b"));
        public void noop() {}
    }

    @Test
    void rendersChildrenWithInputsProjectionAndHostAttributes() {
        LOG.clear();
        final var page = new ViewFixture.Page(true, Card.class, Parent.class);
        final var html = page.render(Parent.class);

        assertEquals("<app-parent data-jsp-v=\"1\">"
                + "<app-card class=\"c on\" data-jsp-v=\"2\" data-jsp-on=\"click:1.1\"><h2>Hello</h2><b slot=\"header\">H</b><div class=\"body\">body Hello</div>3</app-card>Hello\n"
                + "<app-card data-jsp-v=\"3\"><h2>a</h2><div class=\"body\">fallback</div>1</app-card><app-card data-jsp-v=\"4\"><h2>b</h2><div class=\"body\">fallback</div>1</app-card>"
                + "</app-parent>", html);
        assertEquals(List.of("constructor", "ngOnChanges [title, size]", "ngOnInit", "ngDoCheck", "ngAfterContentInit", "ngAfterContentChecked",
                "ngAfterViewInit", "ngAfterViewChecked"), LOG.subList(0, 8));
    }

    @Test
    void reRenderKeepsInstancesRunsCheckHooksAndDestroysRemovedChildren() {
        final var page = new ViewFixture.Page(false, Card.class, Parent.class);
        page.render(Parent.class);
        final var parent = (Parent) page.root.instance();
        final var firstCard = page.tree.view(2).instance();
        final var cardB = page.tree.view(4).instance();
        LOG.clear();

        parent.heading = "Changed";
        parent.names.remove("a");
        final var html = page.refresh();

        assertSame(firstCard, page.tree.view(2).instance());
        assertSame(cardB, page.tree.view(4).instance());
        assertTrue(html.contains("<h2>Changed</h2>"));
        assertTrue(html.contains("data-jsp-v=\"4\""));
        assertEquals(List.of("ngOnChanges [title]", "ngDoCheck", "ngAfterContentChecked", "ngAfterViewChecked",
                "ngDoCheck", "ngAfterContentChecked", "ngAfterViewChecked", "ngOnDestroy a"), LOG);
        assertEquals(3, page.tree.size());
    }

    @Test
    void outputsRunTheParentHandlerInTheElementScope() {
        final var page = new ViewFixture.Page(false, Card.class, Parent.class);
        page.render(Parent.class);
        final var parent = (Parent) page.root.instance();

        ((Card) page.tree.view(2).instance()).saved.emit("x");
        assertEquals("x", parent.last);
        ((Card) page.tree.view(4).instance()).saved.emit("y");
        assertEquals("b:y", parent.last);
    }

    @Component(selector = "app-counter", template = "{{ value() }}")
    public static class Counter {
        public ModelSignal<Integer> value = Signals.model(0);
    }

    @Component(selector = "app-form", template = "<app-counter [(value)]=\"count\"></app-counter>:{{ count }}")
    public static class Form {
        public int count = 5;
    }

    @Test
    void twoWayBindingOnAModelSignalWritesBackToTheParent() {
        final var page = new ViewFixture.Page(false, Counter.class, Form.class);
        assertTrue(page.render(Form.class).contains("5</app-counter>:5"));

        ((Counter) page.tree.view(2).instance()).value.set(9);
        assertEquals(9, ((Form) page.root.instance()).count);
        assertTrue(page.refresh().contains("9</app-counter>:9"));
    }

    @Component(selector = "app-slots", template = "<ng-content select=\"h1\"></ng-content>|<ng-content select=\".note\"></ng-content>|<ng-content></ng-content>")
    public static class Slots {}

    @Component(selector = "app-slot-user", template = "<app-slots><p ngProjectAs=\"h1\">as-h1</p><span class=\"note\">n</span>text<i>rest</i></app-slots>")
    public static class SlotUser {}

    @Test
    void projectsBySelectorAndNgProjectAs() {
        final var page = new ViewFixture.Page(false, Slots.class, SlotUser.class);
        assertEquals("<app-slot-user data-jsp-v=\"1\"><app-slots data-jsp-v=\"2\"><p>as-h1</p>|<span class=\"note\">n</span>|text<i>rest</i></app-slots></app-slot-user>",
                page.render(SlotUser.class));
    }

    @Component(selector = "app-inner", template = "inner")
    public static class Inner {}

    @Component(selector = "app-middle", template = "<ng-content></ng-content>")
    public static class Middle {}

    @Component(selector = "app-outer", template = "<app-middle><app-inner></app-inner></app-middle>")
    public static class Outer {}

    @Test
    void projectedComponentsBelongToTheDeclaringView() {
        final var page = new ViewFixture.Page(false, Inner.class, Middle.class, Outer.class);
        assertEquals("<app-outer data-jsp-v=\"1\"><app-middle data-jsp-v=\"2\"><app-inner data-jsp-v=\"3\">inner</app-inner></app-middle></app-outer>",
                page.render(Outer.class));
        assertSame(page.root, page.tree.view(3).parent());
        assertEquals(0, page.tree.view(2).children().size());
        assertEquals(2, page.root.children().size());
        assertNotSame(page.tree.view(2), page.tree.view(3).parent());
    }
}
