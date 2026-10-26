package dev.isira.jaspring.template.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CssSelectorTest {

    @Test
    void parsesElementClassAttributeAndNotParts() {
        final var selectors = CssSelector.parse("button[primary].big:not([disabled]), .only-class, [attr=value], *, #id");
        assertEquals("button.big[primary]:not([disabled])", selectors.get(0).toString());
        assertEquals(".only-class", selectors.get(1).toString());
        assertEquals("[attr=value]", selectors.get(2).toString());
        assertEquals("", selectors.get(3).toString());
        assertTrue(selectors.get(3).matches(CssSelector.forElement("anything", Map.of(), Set.of())));
        assertEquals("[id=id]", selectors.get(4).toString());
        assertTrue(CssSelector.parse("app-root").get(0).isElementSelector());
    }

    @Test
    void matchesRenderedElements() {
        final var selector = CssSelector.parse("button[primary].big:not([disabled])").get(0);
        assertTrue(selector.matches(CssSelector.forElement("button", Map.of("class", "big round", "primary", ""), Set.of())));
        assertTrue(selector.matches(CssSelector.forElement("button", Map.of("class", "big"), Set.of("primary"))));
        assertFalse(selector.matches(CssSelector.forElement("button", Map.of("class", "big", "primary", "", "disabled", ""), Set.of())));
        assertFalse(selector.matches(CssSelector.forElement("a", Map.of("class", "big", "primary", ""), Set.of())));

        final var valued = CssSelector.parse("input[type=checkbox]").get(0);
        assertTrue(valued.matches(CssSelector.forElement("input", Map.of("type", "CheckBox"), Set.of())));
        assertFalse(valued.matches(CssSelector.forElement("input", Map.of("type", "text"), Set.of())));
        assertTrue(CssSelector.parse("[appHighlight]").get(0).matches(CssSelector.forElement("p", Map.of("appHighlight", ""), Set.of())));
    }

    @Test
    void rejectsNestedNot() {
        assertThrows(IllegalArgumentException.class, () -> CssSelector.parse(":not(:not(a))"));
    }
}
