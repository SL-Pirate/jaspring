package dev.isira.jaspring.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.signals.InputSignal;
import dev.isira.jaspring.signals.ModelSignal;
import dev.isira.jaspring.signals.OutputEmitter;
import dev.isira.jaspring.signals.RequiredInputException;
import dev.isira.jaspring.signals.Signals;

class ComponentDefsTest {

    @Component(selector = "app-card, [appCard]", template = "<p>{{ title }}</p>", changeDetection = ChangeDetectionStrategy.ON_PUSH)
    public static class Card {
        @Input public String title = "none";
        @Input("count") public int amount;
        @Input(required = true) public String id;
        public InputSignal<String> label = Signals.input("l");
        public InputSignal<Integer> size = Signals.inputRequired();
        public ModelSignal<String> value = Signals.model("v");
        public OutputEmitter<String> saved = Signals.output();
        @Output("closed") public OutputEmitter<Void> close = Signals.output();
        private final List<String> log = new ArrayList<>();
    }

    @Test
    void readsSelectorInputsAndOutputs() {
        final var def = ComponentDefs.of(Card.class, 3);

        assertEquals(2, def.selectors().size());
        assertEquals(ChangeDetectionStrategy.ON_PUSH, def.changeDetection());
        assertEquals(List.of("title", "count", "id", "label", "size", "value"), List.copyOf(def.inputs().keySet()));
        assertEquals(List.of("value" + "Change", "saved", "closed"), List.copyOf(def.outputs().keySet()));
        assertTrue(def.inputs().get("id").required());
        assertEquals(InputDef.Kind.SIGNAL, def.inputs().get("label").kind());
        assertEquals(InputDef.Kind.MODEL, def.inputs().get("value").kind());
        assertEquals("jsp-c3", def.scopeId());
    }

    @Test
    void appliesInputsOfEveryKind() {
        final var def = ComponentDefs.of(Card.class, 0);
        final var card = new Card();
        ComponentDefs.bindSignalNames(card);

        def.inputs().get("count").apply(card, 4);
        def.inputs().get("label").apply(card, "hello");
        def.inputs().get("value").apply(card, "from parent");
        assertEquals(4, card.amount);
        assertEquals("hello", card.label.get());
        assertEquals("from parent", card.value.get());

        final var error = assertThrows(RequiredInputException.class, () -> card.size.get());
        assertEquals("Input 'size' is required but no value is available yet", error.getMessage());
    }

    @Test
    void outputsDeliverToSubscribers() {
        final var def = ComponentDefs.of(Card.class, 0);
        final var card = new Card();
        final var received = new ArrayList<Object>();
        def.outputs().get("saved").subscribe(card, received::add);
        def.outputs().get("valueChange").subscribe(card, received::add);

        card.saved.emit("s");
        card.value.set("typed");
        assertEquals(List.of("s", "typed"), received);
    }

    @Component(selector = "app-bad")
    public static class NoTemplate {}

    @Test
    void componentNeedsATemplate() {
        final var error = assertThrows(IllegalStateException.class, () -> ComponentDefs.of(NoTemplate.class, 0));
        assertEquals("Component NoTemplate needs a template or a templateUrl", error.getMessage());
    }

    @Component(selector = "app-file", templateUrl = "component-defs-test.html")
    public static class FromFile {}

    @Test
    void templateUrlResolvesRelativeToThePackage() {
        assertEquals("<p>from file</p>", ComponentDefs.of(FromFile.class, 0).templateSource().trim());
    }
}
