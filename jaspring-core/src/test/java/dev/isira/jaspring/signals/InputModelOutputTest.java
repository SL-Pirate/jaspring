package dev.isira.jaspring.signals;

import static dev.isira.jaspring.signals.Signals.computed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.signals.internal.InputSignalNode;
import dev.isira.jaspring.signals.internal.ModelSignalNode;

class InputModelOutputTest {

    @Test
    void optionalInputStartsWithItsInitialValue() {
        final var name = Signals.input("anon");
        assertEquals("anon", name.get());
        assertNull(Signals.input().get());
    }

    @Test
    void requiredInputThrowsUntilBound() {
        final var name = Signals.<String>inputRequired();
        ((InputSignalNode<?>) name).bindName("name");

        final var error = assertThrows(RequiredInputException.class, name::get);
        assertEquals("Input 'name' is required but no value is available yet", error.getMessage());

        ((InputSignalNode<?>) name).applyValue("Ann");
        assertEquals("Ann", name.get());
    }

    @Test
    void boundValuesGoThroughTheTransformAndNotifyReaders() {
        final var count = Signals.input(0, raw -> Integer.parseInt(raw.toString()));
        final var twice = computed(() -> count.get() * 2);

        assertEquals(0, twice.get());
        ((InputSignalNode<?>) count).applyValue("21");
        assertEquals(42, twice.get());
    }

    @Test
    void modelSetEmitsToSubscribersButBoundValuesDoNot() {
        final var value = Signals.model("a");
        final var emitted = new ArrayList<String>();
        value.subscribe(emitted::add);

        value.set("b");
        value.set("b");
        value.update(v -> v + "c");
        ((ModelSignalNode<?>) value).applyValue("parent");

        assertEquals(List.of("b", "bc"), emitted);
        assertEquals("parent", value.get());
    }

    @Test
    void outputDeliversToEverySubscriberUntilUnsubscribed() {
        final var clicked = Signals.<Integer>output();
        final var first = new ArrayList<Integer>();
        final var second = new ArrayList<Integer>();
        final var subscription = clicked.subscribe(first::add);
        clicked.subscribe(second::add);

        clicked.emit(1);
        subscription.unsubscribe();
        clicked.emit(2);

        assertEquals(List.of(1), first);
        assertEquals(List.of(1, 2), second);
    }
}
