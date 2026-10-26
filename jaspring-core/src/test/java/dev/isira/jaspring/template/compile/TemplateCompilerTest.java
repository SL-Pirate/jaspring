package dev.isira.jaspring.template.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.ComponentFactory;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.component.Input;
import dev.isira.jaspring.component.Schema;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;
import dev.isira.jaspring.template.parse.TemplateCompileException;

class TemplateCompilerTest {

    private static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();
    private static final TemplateCompiler COMPILER = new TemplateCompiler(new GraalAngularTemplateParser(RUNTIME), DomSchema.load(RUNTIME));

    @Component(selector = "app-child", template = "{{ name }}")
    public static class Child {
        @Input public String name;
        @Input(required = true) public int size;
    }

    @Component(selector = "app-ok", template = """
            <p [title]="title" (click)="save($event)" [class.on]="on">{{ title | upper }}</p>
            @for (item of items; track item.id; let i = $index) {<app-child [name]="item.label" size="1" (nameChange)="n = $event"></app-child>{{ i }}}
            <ng-template #tpl let-x>{{ x }}{{ title }}</ng-template><ng-container *ngTemplateOutlet="tpl"></ng-container>
            <input [(ngModel)]="title" #box>{{ box.value }}{{ $any(items).length }}{{ this.title }}
            @let doubled = count * 2; {{ doubled }}
            """)
    public static class Ok {
        public String title;
        public boolean on;
        public int count;
        public List<Item> items;
        public String n;
        public void save(Object event) {}
        public record Item(long id, String label) {}
    }

    private static ComponentRegistry registry(Class<?>... components) {
        final var registry = new ComponentRegistry(ComponentFactory.reflective());
        registry.registerPipe("upper", (value, args) -> String.valueOf(value).toUpperCase());
        for (final var component : components) {
            registry.registerComponent(component);
        }
        return registry;
    }

    @Test
    void validTemplatesCompile() {
        final var registry = registry(Child.class, Ok.class);
        registry.compileAll(COMPILER);
        assertTrue(registry.template(Ok.class).parsed().nodes().size() > 5);
    }

    private static TemplateCompileException compileError(Class<?> component, Class<?>... others) {
        final var registry = registry(others);
        registry.registerComponent(component);
        return assertThrows(TemplateCompileException.class, () -> registry.compileAll(COMPILER));
    }

    @Component(selector = "app-a", template = "<p>{{ missing }}</p>")
    public static class UnknownMember {
        public String present;
    }

    @Test
    void unknownMemberIsReportedLikeAngular() {
        final var error = compileError(UnknownMember.class);
        assertEquals("Property 'missing' does not exist on type 'UnknownMember'.", error.errors().get(0).message());
        assertTrue(error.errors().get(0).text().endsWith("UnknownMember.template@0:6"), error.errors().get(0).text());
    }

    @Component(selector = "app-b", template = "<app-nope></app-nope><custom-thing></custom-thing>")
    public static class UnknownElements {}

    @Test
    void unknownElementsAreReported() {
        final var error = compileError(UnknownElements.class);
        assertEquals(2, error.errors().size());
        assertTrue(error.errors().get(0).message().startsWith("'app-nope' is not a known element"));
    }

    @Component(selector = "app-c", template = "<custom-thing [prop]=\"1\"></custom-thing>", schemas = Schema.CUSTOM_ELEMENTS)
    public static class CustomElements {}

    @Test
    void customElementsSchemaAllowsUnknownTagsAndProperties() {
        final var registry = registry(CustomElements.class);
        registry.compileAll(COMPILER);
    }

    @Component(selector = "app-d", template = "<div [nope]=\"1\"></div><app-child [other]=\"2\" size=\"1\"></app-child>")
    public static class UnknownProperties {}

    @Test
    void unknownPropertiesAreReported() {
        final var error = compileError(UnknownProperties.class, Child.class);
        assertEquals(2, error.errors().size());
        assertTrue(error.errors().get(0).message().startsWith("Can't bind to 'nope' since it isn't a known property of 'div'."));
        assertTrue(error.errors().get(1).message().contains("If 'other' is an input of 'Child'"));
    }

    @Component(selector = "app-e", template = "<app-child name=\"x\"></app-child>{{ 1 | nope }}")
    public static class MissingRequiredAndPipe {}

    @Test
    void requiredInputsAndPipesAreChecked() {
        final var error = compileError(MissingRequiredAndPipe.class, Child.class);
        assertEquals("Required input 'size' from component Child must be specified.", error.errors().get(0).message());
        assertTrue(error.errors().get(1).message().startsWith("The pipe 'nope' could not be found"));
    }
}
