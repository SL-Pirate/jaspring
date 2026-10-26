package dev.isira.jaspring.template.eval;

import static dev.isira.jaspring.template.eval.EvalFixture.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HandlerAssignmentTest {

    private final EvalFixture.Person person = new EvalFixture.Person();
    private final EvaluationContext ctx = EvaluationContext.of(person);

    @Test
    void assignsFieldsAndReturnsTheValue() {
        assertEquals("Bob", run("name = 'Bob'", ctx));
        assertEquals("Bob", person.name);
    }

    @Test
    void compoundAssignmentsReadModifyWrite() {
        run("age += 1", ctx);
        assertEquals(31, person.age);
        run("age *= 2", ctx);
        assertEquals(62, person.age);
        run("name += '!'", ctx);
        assertEquals("Ann!", person.name);
    }

    @Test
    void logicalAssignmentsShortCircuit() {
        run("flag ||= true", ctx);
        assertTrue(person.flag);
        run("flag &&= false", ctx);
        assertFalse(person.flag);
        run("friend ??= this", ctx);
        assertEquals(person, person.friend);
    }

    @Test
    void assigningToAWritableSignalWritesThroughIt() {
        run("count = 5", ctx);
        assertEquals(5, person.count.get());
        run("count.set(count() + 1)", ctx);
        assertEquals(6, person.count.get());
        run("count.update(c => c + 10)", ctx);
        assertEquals(16, person.count.get());
    }

    @Test
    void writesMapKeysAndListIndexes() {
        run("meta.k = 2", ctx);
        assertEquals(2L, person.meta.get("k"));
        run("meta['fresh'] = 'x'", ctx);
        assertEquals("x", person.meta.get("fresh"));
        run("tags[0] = 'z'", ctx);
        assertEquals("z", person.tags.get(0));
        assertThrows(EvaluationException.class, () -> run("point.x = 9", ctx), "records are immutable");
    }

    @Test
    void chainsRunInOrderAndReturnTheLastValue() {
        final var result = run("name = 'A'; age = 1; greet(name)", ctx);
        assertEquals("hi A", result);
        assertEquals(1, person.age);
    }

    @Test
    void eventPayloadIsReadThroughTheScope() {
        final var event = new HashMap<String, Object>(Map.of("type", "input", "target", Map.of("value", "typed")));
        final var scoped = ctx.withScope(Scope.root().with("$event", event));

        run("name = $event.target.value", scoped);
        assertEquals("typed", person.name);
    }

    @Test
    void templateVariablesCannotBeAssigned() {
        final var scoped = ctx.withScope(Scope.root().with("item", "x"));
        final var error = assertThrows(EvaluationException.class, () -> run("item = 'y'", scoped));
        assertEquals("Cannot assign value to template variable 'item'", error.getMessage());
    }

    @Test
    void handlerReturningFalseIsVisibleToTheCaller() {
        assertEquals(false, run("age = 1; false", ctx));
    }
}
