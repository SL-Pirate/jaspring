package dev.isira.jaspring.template.eval;

import static dev.isira.jaspring.template.eval.EvalFixture.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.isira.jaspring.pipe.PipeRegistry;
import dev.isira.jaspring.template.expr.JsUndefined;

class ExpressionEvaluatorTest {

    private final EvalFixture.Person person = new EvalFixture.Person();
    private final EvaluationContext ctx = EvaluationContext.of(person).withPipes(PipeRegistry.of(Map.of(
            "upper", (value, args) -> Js.toJsString(value).toUpperCase(),
            "slice", (value, args) -> Js.toJsString(value).substring(((Number) args[0]).intValue(), ((Number) args[1]).intValue()))));

    static Stream<Arguments> jsSemantics() {
        return Stream.of(
                Arguments.of("1 + 2", "3"),
                Arguments.of("1 + '2'", "12"),
                Arguments.of("'a' + 1", "a1"),
                Arguments.of("6 / 3", "2"),
                Arguments.of("5 / 2", "2.5"),
                Arguments.of("2 ** 10", "1024"),
                Arguments.of("7 % 3", "1"),
                Arguments.of("1.5 + 1.5", "3"),
                Arguments.of("0.1 + 0.2", "0.30000000000000004"),
                Arguments.of("null ?? 'x'", "x"),
                Arguments.of("0 ?? 'x'", "0"),
                Arguments.of("undefined ?? 'y'", "y"),
                Arguments.of("0 || 'x'", "x"),
                Arguments.of("'' && 'x'", ""),
                Arguments.of("1 && 2", "2"),
                Arguments.of("1 == '1'", "true"),
                Arguments.of("1 === '1'", "false"),
                Arguments.of("null == undefined", "true"),
                Arguments.of("null === undefined", "false"),
                Arguments.of("1 < 2", "true"),
                Arguments.of("'a' < 'b'", "true"),
                Arguments.of("2 >= 2", "true"),
                Arguments.of("!0", "true"),
                Arguments.of("!'a'", "false"),
                Arguments.of("![]", "false"),
                Arguments.of("typeof 'a'", "string"),
                Arguments.of("typeof 1", "number"),
                Arguments.of("typeof null", "object"),
                Arguments.of("typeof undefined", "undefined"),
                Arguments.of("typeof greet", "function"),
                Arguments.of("typeof count", "function"),
                Arguments.of("-age", "-30"),
                Arguments.of("+'3'", "3"),
                Arguments.of("age > 18 ? 'adult' : 'minor'", "adult"),
                Arguments.of("`Hi ${name}`", "Hi Ann"),
                Arguments.of("`${friend}`", "null"),
                Arguments.of("[1, ...tags]", "1,a,b"),
                Arguments.of("({a: 1, ...meta}).k", "1"),
                Arguments.of("'k' in meta", "true"),
                Arguments.of("'zz' in meta", "false"),
                Arguments.of("void 1", "undefined"),
                Arguments.of("(1 + 2) * 3", "9"));
    }

    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("jsSemantics")
    void followsJavaScriptSemantics(String expression, String expected) {
        assertEquals(expected, Js.toJsString(eval(expression, ctx)));
    }

    static Stream<Arguments> memberAccess() {
        return Stream.of(
                Arguments.of("name", "Ann"),
                Arguments.of("title", "Dr"),
                Arguments.of("active", "true"),
                Arguments.of("secret", "hidden"),
                Arguments.of("point.x", "3"),
                Arguments.of("point.sum()", "7"),
                Arguments.of("this.name", "Ann"),
                Arguments.of("friend?.name", "null"),
                Arguments.of("friend?.name.length", "null"),
                Arguments.of("friend?.greet('x')", "null"),
                Arguments.of("friend?.['name']", "null"),
                Arguments.of("tags.length", "2"),
                Arguments.of("tags[0]", "a"),
                Arguments.of("tags[5]", "undefined"),
                Arguments.of("tags.includes('b')", "true"),
                Arguments.of("tags.indexOf('b')", "1"),
                Arguments.of("tags.at(-1)", "b"),
                Arguments.of("tags.map(t => t.toUpperCase()).join('-')", "A-B"),
                Arguments.of("tags.filter(t => t !== 'a').length", "1"),
                Arguments.of("tags.some(t => t === 'b')", "true"),
                Arguments.of("tags.find(t => t === 'zz')", "undefined"),
                Arguments.of("tags.slice(1)", "b"),
                Arguments.of("name.toUpperCase()", "ANN"),
                Arguments.of("name.length", "3"),
                Arguments.of("name.startsWith('A')", "true"),
                Arguments.of("name.slice(0, 2)", "An"),
                Arguments.of("name.split('n')", "A,,"),
                Arguments.of("name.padStart(5, '*')", "**Ann"),
                Arguments.of("meta.k", "1"),
                Arguments.of("meta['k']", "1"),
                Arguments.of("meta.missing", "undefined"),
                Arguments.of("greet('Bob')", "hi Bob"),
                Arguments.of("twice(4)", "8"),
                Arguments.of("twice(2.0)", "4"),
                Arguments.of("pick('a')", "one:a"),
                Arguments.of("pick('a', 'b')", "two:ab"),
                Arguments.of("countMatching(t => t === 'a')", "1"),
                Arguments.of("count()", "0"),
                Arguments.of("doubled()", "0"),
                Arguments.of("(1.5).toFixed(1)", "1.5"),
                Arguments.of("/an/i.test(name)", "true"),
                Arguments.of("reset()", "undefined"),
                Arguments.of("resetCount", "0"));
    }

    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("memberAccess")
    void readsJavaMembersLikeAngularTemplates(String expression, String expected) {
        assertEquals(expected, Js.toJsString(eval(expression, ctx)));
    }

    @Test
    void arithmeticKeepsIntegralResultsAsLong() {
        assertEquals(3L, eval("1 + 2", ctx));
        assertEquals(2L, eval("6 / 3", ctx));
        assertEquals(2.5, eval("5 / 2", ctx));
        assertInstanceOf(Integer.class, eval("twice(4)", ctx));
    }

    @Test
    void localsShadowComponentMembers() {
        final var scoped = ctx.withScope(Scope.root().with("name", "Local").with("extra", 7L));

        assertEquals("Local", eval("name", scoped));
        assertEquals(7L, eval("extra", scoped));
        assertEquals("Ann", eval("this.name", scoped));
        assertEquals(JsUndefined.INSTANCE, eval("extra", ctx));
    }

    @Test
    void interpolationRendersNullishAsEmptyText() {
        final var text = ExpressionEvaluator.evaluate(
                ((dev.isira.jaspring.template.expr.Expr.Interpolation) ((dev.isira.jaspring.template.ast.TemplateNode.BoundText)
                        EvalFixture.PARSER.parse("a{{ friend }}b{{ name }}c{{ 1.0 }}", "t.html",
                                dev.isira.jaspring.template.parse.ParseOptions.defaults()).nodes().get(0)).value()),
                ctx);
        assertEquals("abAnnc1", text);
    }

    @Test
    void pipesReceiveValueAndArguments() {
        assertEquals("ANN", eval("name | upper", ctx));
        assertEquals("n", eval("name | slice:1:2", ctx));
        assertEquals("N", eval("(name | slice:1:2) | upper", ctx));
    }

    @Test
    void unknownPipeIsAnError() {
        final var error = assertThrows(EvaluationException.class, () -> eval("name | nope", ctx));
        assertEquals("The pipe 'nope' could not be found", error.getMessage());
    }

    @Test
    void readingAMemberOfNullIsATypeError() {
        final var error = assertThrows(EvaluationException.class, () -> eval("friend.name", ctx));
        assertEquals("Cannot read properties of null (reading 'name')", error.getMessage());
    }

    @Test
    void callingANonFunctionIsATypeError() {
        assertEquals("name is not a function", assertThrows(EvaluationException.class, () -> eval("name()", ctx)).getMessage());
        assertEquals("nope is not a function", assertThrows(EvaluationException.class, () -> eval("nope()", ctx)).getMessage());
    }

    @Test
    void arrayAndObjectLiteralsProduceJavaCollections() {
        assertEquals(List.of(1L, "a", "b"), eval("[1, ...tags]", ctx));
        assertEquals(Map.of("a", 1L, "k", 1), eval("{a: 1, ...meta}", ctx));
    }

    @Test
    void signalReadsRegisterWithTheActiveConsumer() {
        final var reads = dev.isira.jaspring.signals.Signals.computed(() -> eval("count() + doubled()", ctx));
        assertEquals(0L, reads.get());
        person.count.set(2);
        assertEquals(6L, reads.get());
    }

    @Test
    void unknownMembersAreUndefinedAndFalsy() {
        assertEquals(JsUndefined.INSTANCE, eval("nope", ctx));
        assertTrue((Boolean) eval("!nope", ctx));
        assertNull(eval("nope?.deeper", ctx));
    }
}
