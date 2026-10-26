package dev.isira.jaspring.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.ComponentDefs;
import dev.isira.jaspring.component.ComponentFactory;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.template.compile.TemplateCompiler;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;

class TailwindProcessorTest {

    private static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();
    private static final TailwindProcessor TAILWIND = new TailwindProcessor(RUNTIME, """
            @import "tailwindcss";
            @theme { --color-brand: #0f766e; }
            """);

    @Component(selector = "app-tw", template = """
            <div class="mx-auto max-w-3xl p-6" [class.hidden]="off" [ngClass]="{'text-brand': on, 'font-bold': !on}" [class]="on ? 'rounded-lg' : 'rounded'">
              @for (x of xs; track x) {<span class="px-2 hover:underline">{{ x }}</span>}
            </div>
            """, styles = ":host { @apply block border border-brand; } .note { color: var(--color-brand); }")
    public static class TailwindComponent {
        public boolean on;
        public boolean off;
        public List<String> xs = List.of();
    }

    @Test
    void generatesUtilitiesOnlyForTheCandidates() {
        final var css = TAILWIND.global(Set.of("p-6", "text-brand", "hover:underline", "not-a-utility", "md:flex"));

        assertTrue(css.contains(".p-6"), css);
        assertTrue(css.contains("--color-brand: #0f766e"), css);
        assertTrue(css.contains(".text-brand"), css);
        assertTrue(css.contains(".hover\\:underline"), css);
        assertTrue(css.contains(".md\\:flex"), css);
        assertFalse(css.contains("not-a-utility"), css);
        assertFalse(css.contains(".m-4"), "unused utilities are not emitted");
        assertTrue(css.contains("*, ::after, ::before"), "preflight is included: " + css.substring(0, 200));
    }

    @Test
    void candidatesComeFromTemplatesAndExtraHtml() {
        final var registry = new ComponentRegistry(ComponentFactory.reflective());
        registry.registerComponent(TailwindComponent.class);
        registry.compileAll(new TemplateCompiler(new GraalAngularTemplateParser(RUNTIME), DomSchema.load(RUNTIME)));

        final var candidates = TailwindCandidates.of(registry, "<body class=\"bg-gray-50 antialiased\">");

        assertTrue(candidates.containsAll(Set.of("mx-auto", "max-w-3xl", "p-6", "hidden", "text-brand", "font-bold", "rounded-lg", "rounded",
                "px-2", "hover:underline", "bg-gray-50", "antialiased")), candidates.toString());
        assertFalse(candidates.contains("x"), "loop variables are not class names");
    }

    @Test
    void componentStylesResolveApplyBeforeScoping() {
        final var def = ComponentDefs.of(TailwindComponent.class, 7);
        final var scoped = new StyleProcessor(RUNTIME, TAILWIND::componentStyle).process(def);

        assertTrue(scoped.contains("[_nghost-jsp-c7]"), scoped);
        assertTrue(scoped.contains("display: block"), scoped);
        assertTrue(scoped.contains("border-style: var(--tw-border-style)") || scoped.contains("border-width: 1px"), scoped);
        assertTrue(scoped.contains("var(--color-brand)"), scoped);
        assertFalse(scoped.contains("@apply"), scoped);
        assertEquals("p { color: red }", new StyleProcessor(RUNTIME, TAILWIND::componentStyle).process(ComponentDefs.of(Plain.class, 0)).replace("[_ngcontent-jsp-c0]", ""));
    }

    @Component(selector = "app-plain", template = "x", styles = "p { color: red }")
    public static class Plain {}

    @Test
    void reportsTailwindErrors() {
        final var error = assertThrows(IllegalStateException.class, () -> TAILWIND.componentStyle(".x { @apply no-such-utility; }"));
        assertTrue(error.getMessage().contains("no-such-utility"), error.getMessage());
    }
}
