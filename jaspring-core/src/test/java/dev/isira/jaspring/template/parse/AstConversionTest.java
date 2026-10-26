package dev.isira.jaspring.template.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.template.ast.BindingType;
import dev.isira.jaspring.template.ast.DeferredTrigger;
import dev.isira.jaspring.template.ast.EventType;
import dev.isira.jaspring.template.ast.I18nNode;
import dev.isira.jaspring.template.ast.SecurityContext;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.expr.BinaryOp;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.expr.JsUndefined;

class AstConversionTest {

    private static List<TemplateNode> parse(String html) {
        return ParserFixture.PARSER.parse(html, "test.html", ParseOptions.defaults()).nodes();
    }

    @Test
    void angularErrorsKeepTheirWordingAndPosition() {
        final var error = assertThrows(TemplateCompileException.class, () -> parse("<div>\n  @for (x of xs) {}\n</div>"));

        assertEquals(1, error.errors().size());
        final var first = error.errors().get(0);
        assertEquals("@for loop must have a \"track\" expression", first.message());
        assertEquals(1, first.line());
        assertEquals(2, first.col());
        assertTrue(first.text().endsWith("test.html@1:2"), first.text());
        assertEquals(TemplateError.Level.ERROR, first.level());
    }

    @Test
    void spansPointIntoTheTemplate() {
        final var html = "<p>{{ name }}</p>";
        final var element = (TemplateNode.Element) parse(html).get(0);

        assertEquals(html, element.sourceSpan().textIn(html));
        assertEquals("<p>", element.startSourceSpan().textIn(html));
        final var text = (TemplateNode.BoundText) element.children().get(0);
        final var interpolation = (Expr.Interpolation) text.value();
        final var read = (Expr.PropertyRead) interpolation.expressions().get(0);
        assertEquals("name", html.substring(read.span().absoluteStart(), read.span().absoluteEnd()));
    }

    @Test
    void bindingKindsAndSecurityContextsComeFromAngular() {
        final var element = (TemplateNode.Element) parse(
                "<a [href]=\"u\" [attr.title]=\"t\" [class.on]=\"c\" [style.width.px]=\"w\" [innerHTML]=\"h\" (click)=\"go()\" (window:resize)=\"r()\"></a>")
                .get(0);

        final var inputs = element.inputs();
        assertEquals(BindingType.PROPERTY, inputs.get(0).type());
        assertEquals(SecurityContext.URL, inputs.get(0).securityContext());
        assertEquals(BindingType.ATTRIBUTE, inputs.get(1).type());
        assertEquals("title", inputs.get(1).name());
        assertEquals(BindingType.CLASS, inputs.get(2).type());
        assertEquals("on", inputs.get(2).name());
        assertEquals(BindingType.STYLE, inputs.get(3).type());
        assertEquals("width", inputs.get(3).name());
        assertEquals("px", inputs.get(3).unit());
        assertEquals(SecurityContext.HTML, inputs.get(4).securityContext());
        assertEquals(EventType.REGULAR, element.outputs().get(0).type());
        assertNull(element.outputs().get(0).target());
        assertEquals("window", element.outputs().get(1).target());
        assertEquals("resize", element.outputs().get(1).name());
    }

    @Test
    void twoWayBindingDesugarsIntoInputAndChangeEvent() {
        final var element = (TemplateNode.Element) parse("<input [(ngModel)]=\"name\">").get(0);

        assertEquals(BindingType.TWO_WAY, element.inputs().get(0).type());
        assertEquals("ngModel", element.inputs().get(0).name());
        assertEquals("ngModelChange", element.outputs().get(0).name());
        assertEquals(EventType.TWO_WAY, element.outputs().get(0).type());
        assertInstanceOf(Expr.PropertyRead.class, element.outputs().get(0).handler());
        assertTrue(element.isVoid());
    }

    @Test
    void structuralDirectivesDesugarIntoTemplates() {
        final var template = (TemplateNode.Template) parse(
                "<li *ngFor=\"let item of items; trackBy: byId; let i = index\">{{ item }}</li>").get(0);

        assertEquals("li", template.tagName());
        assertEquals(List.of("ngFor", "ngForOf", "ngForTrackBy"), template.templateAttrs().stream().map(a -> a.name()).toList());
        assertEquals(List.of("item", "i"), template.variables().stream().map(TemplateNode.Variable::name).toList());
        assertEquals(List.of("$implicit", "index"), template.variables().stream().map(TemplateNode.Variable::value).toList());
        assertInstanceOf(TemplateNode.Element.class, template.children().get(0));
    }

    @Test
    void forBlockCarriesImplicitVariablesAndTrack() {
        final var block = (TemplateNode.ForLoopBlock) parse("@for (u of users; track u.id; let i = $index) {x} @empty {none}").get(0);

        assertEquals("u", block.item().name());
        assertEquals("$implicit", block.item().value());
        assertTrue(block.contextVariables().stream().map(TemplateNode.Variable::name).toList()
                .containsAll(List.of("$index", "$first", "$last", "$even", "$odd", "$count", "i")));
        assertInstanceOf(Expr.PropertyRead.class, block.trackBy());
        assertEquals(1, block.empty().children().size());
    }

    @Test
    void deferTriggersKeepTheirKindAndOrder() {
        final var block = (TemplateNode.DeferredBlock) parse(
                "@defer (on idle; on timer(500ms); when ready; prefetch on hover(btn); hydrate on interaction) {x} @placeholder (minimum 1s) {p} @loading (after 100ms; minimum 2s) {l} @error {e}")
                .get(0);

        assertEquals(List.of(DeferredTrigger.Idle.class, DeferredTrigger.Timer.class, DeferredTrigger.When.class),
                block.triggers().stream().map(t -> (Class<?>) t.getClass()).toList());
        assertEquals(500, ((DeferredTrigger.Timer) block.triggers().get(1)).delay());
        assertEquals("btn", ((DeferredTrigger.Hover) block.prefetchTriggers().get(0)).reference());
        assertInstanceOf(DeferredTrigger.Interaction.class, block.hydrateTriggers().get(0));
        assertEquals(1000, block.placeholder().minimumTime());
        assertEquals(100, block.loading().afterTime());
        assertEquals(2000, block.loading().minimumTime());
        assertEquals(1, block.error().children().size());
    }

    @Test
    void switchBlockGroupsConsecutiveCasesAndDefault() {
        final var block = (TemplateNode.SwitchBlock) parse("@switch (x) { @case (1) @case (2) {a} @default {d} }").get(0);

        assertEquals(2, block.groups().size());
        assertEquals(2, block.groups().get(0).cases().size());
        assertNull(block.groups().get(1).cases().get(0).expression());
    }

    @Test
    void icuCarriesVarsPlaceholdersAndCases() {
        final var icu = (TemplateNode.Icu) parse("{count, plural, =0 {none} =1 {one <b>{{ name }}</b>} other {many}}").get(0);

        assertEquals(1, icu.vars().size());
        assertEquals("plural", icu.message().type());
        assertEquals("count", icu.message().expression());
        assertEquals(List.of("=0", "=1", "other"), List.copyOf(icu.message().cases().keySet()));
        assertTrue(icu.message().cases().get("=1") instanceof I18nNode.Container c
                && c.children().stream().anyMatch(n -> n instanceof I18nNode.TagPlaceholder));
        assertTrue(icu.placeholders().values().stream().anyMatch(p -> p instanceof TemplateNode.BoundText));
    }

    @Test
    void expressionsCoverTheWholeAngularGrammar() {
        final var text = (TemplateNode.BoundText) parse(
                "{{ (a?.b ?? (-c) ** 2) | pipe:1 }}{{ typeof x === 'string' && !y ? [1, ...z] : {k: 1, 'q': 2, ...m} }}{{ `t${w}` }}{{ f(x => x + 1) }}{{ u?.[0] }}{{ undefined }}{{ v! }}")
                .get(0);
        final var expressions = ((Expr.Interpolation) text.value()).expressions();

        final var pipe = assertInstanceOf(Expr.BindingPipe.class, expressions.get(0));
        assertEquals("pipe", pipe.name());
        final var nullish = assertInstanceOf(Expr.Binary.class, ((Expr.ParenthesizedExpression) pipe.exp()).expression());
        assertEquals(BinaryOp.NULLISH, nullish.operation());
        assertInstanceOf(Expr.SafePropertyRead.class, nullish.left());
        final var exp = assertInstanceOf(Expr.Binary.class, nullish.right());
        assertEquals(BinaryOp.EXP, exp.operation());
        assertInstanceOf(Expr.Unary.class, ((Expr.ParenthesizedExpression) exp.left()).expression());
        assertEquals(2L, ((Expr.LiteralPrimitive) exp.right()).value());

        final var conditional = assertInstanceOf(Expr.Conditional.class, expressions.get(1));
        final var and = assertInstanceOf(Expr.Binary.class, conditional.condition());
        assertInstanceOf(Expr.TypeofExpression.class, ((Expr.Binary) and.left()).left());
        assertInstanceOf(Expr.PrefixNot.class, and.right());
        final var array = assertInstanceOf(Expr.LiteralArray.class, conditional.trueExp());
        assertInstanceOf(Expr.SpreadElement.class, array.expressions().get(1));
        final var map = assertInstanceOf(Expr.LiteralMap.class, conditional.falseExp());
        assertEquals(new Expr.MapKey.Property("q", true), map.keys().get(1));
        assertEquals(new Expr.MapKey.Spread(), map.keys().get(2));

        assertInstanceOf(Expr.TemplateLiteral.class, expressions.get(2));
        final var call = assertInstanceOf(Expr.Call.class, expressions.get(3));
        assertInstanceOf(Expr.ArrowFunction.class, call.args().get(0));
        assertInstanceOf(Expr.SafeKeyedRead.class, expressions.get(4));
        assertEquals(JsUndefined.INSTANCE, ((Expr.LiteralPrimitive) expressions.get(5)).value());
        assertInstanceOf(Expr.NonNullAssert.class, expressions.get(6));
    }

    @Test
    void eventHandlersMayAssignAndChain() {
        final var element = (TemplateNode.Element) parse("<button (click)=\"count = count + 1; total += 2; done()\"></button>").get(0);
        final var chain = assertInstanceOf(Expr.Chain.class, element.outputs().get(0).handler());

        assertEquals(3, chain.expressions().size());
        assertEquals(BinaryOp.ASSIGN, ((Expr.Binary) chain.expressions().get(0)).operation());
        assertTrue(((Expr.Binary) chain.expressions().get(1)).operation().isAssignment());
        assertInstanceOf(Expr.Call.class, chain.expressions().get(2));
    }

    @Test
    void lenientParsingReturnsThePartialTree() {
        final var parsed = ParserFixture.PARSER.parse("<a <span></span>", "test.html", new ParseOptions(false, true));
        assertEquals(2, parsed.nodes().size());
    }

    @Test
    void ngContentSelectorsAreCollected() {
        final var parsed = ParserFixture.PARSER.parse("<ng-content select=\"[slot=a]\"></ng-content><ng-content/>", "t.html", ParseOptions.defaults());
        assertEquals(List.of("[slot=a]", "*"), parsed.ngContentSelectors());
    }
}
