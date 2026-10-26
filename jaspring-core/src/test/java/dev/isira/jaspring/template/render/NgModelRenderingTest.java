package dev.isira.jaspring.template.render;

import static dev.isira.jaspring.template.render.RenderFixture.render;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class NgModelRenderingTest {

    public static final class Form {
        public String name = "Ann";
        public boolean agreed = true;
        public String color = "green";
        public String note = "a <b> note";
        public List<String> picks = List.of("b", "c");
        public Object empty;
    }

    private final Form form = new Form();

    @Test
    void reflectsModelValuesIntoInputs() {
        assertEquals("<input value=\"Ann\">", render("<input [ngModel]=\"name\">", form));
        assertEquals("<input type=\"checkbox\" checked=\"\">", render("<input type=\"checkbox\" [ngModel]=\"agreed\">", form));
        assertEquals("<input type=\"radio\" value=\"green\" checked=\"\"><input type=\"radio\" value=\"red\">",
                render("<input type=\"radio\" value=\"green\" [ngModel]=\"color\"><input type=\"radio\" value=\"red\" [ngModel]=\"color\">", form));
        assertEquals("<textarea>a &lt;b&gt; note</textarea>", render("<textarea [ngModel]=\"note\"></textarea>", form));
        assertEquals("<input>", render("<input [ngModel]=\"empty\">", form));
    }

    @Test
    void marksSelectedOptions() {
        assertEquals("<select><option value=\"red\">Red</option><option value=\"green\" selected=\"\">Green</option></select>",
                render("<select [ngModel]=\"color\"><option value=\"red\">Red</option><option value=\"green\">Green</option></select>", form));
        assertEquals("<select><option>green</option><option>blue</option></select>".replace("<option>green", "<option selected=\"\">green"),
                render("<select [ngModel]=\"color\"><option>green</option><option>blue</option></select>", form));
        assertEquals("<select multiple=\"\"><option value=\"a\">a</option><option value=\"b\" selected=\"\">b</option><option value=\"c\" selected=\"\">c</option></select>",
                render("<select multiple [ngModel]=\"picks\">@for (p of ['a','b','c']; track p) {<option [value]=\"p\">{{ p }}</option>}</select>", form));
    }

    @Test
    void twoWayBindingRendersAModelMarkerWhenLive() {
        final var host = new RenderFixture.RecordingHost();
        assertEquals("<input value=\"Ann\" data-jsp-model=\"1.1\">", render("<input [(ngModel)]=\"name\">", form, host));
        assertEquals("<input value=\"Ann\" data-jsp-on=\"input:1.2\" data-jsp-model=\"1.3\">",
                render("<input [ngModel]=\"name\" (input)=\"x()\" (ngModelChange)=\"name = $event\">", form, host));
        assertEquals("<input value=\"Ann\">", render("<input [(ngModel)]=\"name\">", form));
    }
}
