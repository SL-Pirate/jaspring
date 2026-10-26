package dev.isira.jaspring.ide;

import org.junit.Test;

import static org.junit.Assert.*;

public class TemplateSyntaxTest {
    @Test
    public void extractsBindingsInterpolationsAndControlFlowWithoutReadingStaticText() {
        final var text = """
                <!-- {{ ignored }} -->
                <p title="Hi {{ title }}" [hidden]="!visible" (click)="choose($event)">
                  @for (item of items(); track item.id(); let i = $index) {
                    {{ item.name }} {{ i }}
                  } @empty { empty }
                </p>
                <script>const x = '{{ notAnExpression }}';</script>
                """;
        final var document = TemplateSyntax.parse(text);
        assertEquals(6, document.expressions().size());
        assertTrue(document.expressions().stream().anyMatch(e -> e.event() && e.range().substring(text).equals("choose($event)")));
        assertTrue(document.localsAt(text.indexOf("{{ item.name")).stream().anyMatch(l -> l.name().equals("item") && l.iterable()));
        assertTrue(document.localsAt(text.indexOf("empty }")).isEmpty());
    }

    @Test
    public void stringsAndObjectLiteralsDoNotTerminateInterpolation() {
        final var text = "{{ choose({name: '}}'}, value) }}";
        final var expression = TemplateSyntax.parse(text).expressions().getFirst();
        assertEquals(" choose({name: '}}'}, value) ", expression.range().substring(text));
    }

    @Test
    public void nestedLocalsShadowOnlyInsideTheirBlock() {
        final var text = "@for (item of outer; track item) { @for (item of inner; track item) { {{ item }} } {{ item }} } {{ item }}";
        final var syntax = TemplateSyntax.parse(text);
        assertEquals(2, syntax.localsAt(text.indexOf("{{ item")).stream().filter(l -> l.name().equals("item")).count());
        assertEquals(1, syntax.localsAt(text.indexOf("{{ item", text.indexOf("{{ item") + 1)).stream().filter(l -> l.name().equals("item")).count());
        assertTrue(syntax.localsAt(text.lastIndexOf("{{ item")).isEmpty());
    }

    @Test
    public void letIsVisibleAfterDeclarationAndWithinItsView() {
        final var text = "@if (visible) { @let user = selected(); {{ user.name }} } {{ user }}";
        final var syntax = TemplateSyntax.parse(text);
        assertTrue(syntax.localsAt(text.indexOf("selected")).isEmpty());
        assertEquals("user", syntax.localsAt(text.indexOf("user.name")).getFirst().name());
        assertTrue(syntax.localsAt(text.lastIndexOf("user")).isEmpty());
    }

    @Test
    public void handlesUnfinishedExpressionsWhileTyping() {
        final var text = "@for (item of items(); track item) { <p>{{ item.";
        final var syntax = TemplateSyntax.parse(text);
        assertNotNull(syntax.expressionAt(text.length()));
        assertTrue(syntax.localsAt(text.length() - 1).stream().anyMatch(l -> l.name().equals("item")));
    }

    @Test
    public void preservesBindingNameOffsetsAndCanonicalForms() {
        final var banana = BindingName.parse("data-[(value)]");
        assertEquals("value", banana.name());
        assertEquals(7, banana.offset());
        assertEquals(BindingName.Kind.TWO_WAY, banana.kind());
        assertEquals(BindingName.Kind.EVENT, BindingName.parse("on-click").kind());
        assertEquals(BindingName.Kind.REFERENCE, BindingName.parse("ref-input").kind());
        assertFalse(BindingName.parse("class").hasExpression());
    }

    @Test
    public void normalizesResourcesLikeRuntimeAndRejectsTraversalAboveRoot() {
        assertEquals("demo/shared/card.html", JaspringProjectModel.resourcePath("demo.cards", "../shared/./card.html"));
        assertEquals("shared/card.html", JaspringProjectModel.resourcePath("demo.cards", "/shared/card.html"));
        assertNull(JaspringProjectModel.resourcePath("demo", "../../outside.html"));
    }
}
