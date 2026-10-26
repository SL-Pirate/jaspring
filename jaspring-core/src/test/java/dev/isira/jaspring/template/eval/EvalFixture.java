package dev.isira.jaspring.template.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import dev.isira.jaspring.signals.Signal;
import dev.isira.jaspring.signals.Signals;
import dev.isira.jaspring.signals.WritableSignal;
import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.expr.Expr;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;
import dev.isira.jaspring.template.parse.ParseOptions;

/// Parses expressions with the real Angular parser so the evaluator is tested on the AST it will really see.
final class EvalFixture {

    static final GraalAngularTemplateParser PARSER = new GraalAngularTemplateParser(AngularCompilerRuntime.load());

    private EvalFixture() {}

    static Expr expression(String source) {
        final var nodes = PARSER.parse("{{ " + source + " }}", "expr.html", ParseOptions.defaults()).nodes();
        final var text = (TemplateNode.BoundText) nodes.get(0);
        return ((Expr.Interpolation) text.value()).expressions().get(0);
    }

    static Expr handler(String source) {
        final var nodes = PARSER.parse("<a (click)=\"" + source.replace("\"", "&quot;") + "\"></a>", "handler.html", ParseOptions.defaults()).nodes();
        return ((TemplateNode.Element) nodes.get(0)).outputs().get(0).handler();
    }

    static Object eval(String source, EvaluationContext context) {
        return ExpressionEvaluator.evaluate(expression(source), context);
    }

    static Object run(String source, EvaluationContext context) {
        return ExpressionEvaluator.evaluate(handler(source), context);
    }

    /// Looks like a component: public fields, getters, record-style accessors, methods, signals.
    public static final class Person {
        public String name = "Ann";
        public int age = 30;
        public boolean flag;
        public List<String> tags = new ArrayList<>(List.of("a", "b"));
        public Person friend;
        public Map<String, Object> meta = new HashMap<>(Map.of("k", 1));
        public Point point = new Point(3, 4);
        public WritableSignal<Integer> count = Signals.signal(0);
        public Signal<Integer> doubled = Signals.computed(() -> count.get() * 2);
        private String secret = "hidden";

        public String getTitle() {
            return "Dr";
        }

        public boolean isActive() {
            return true;
        }

        public String greet(String who) {
            return "hi " + who;
        }

        public int twice(int x) {
            return x * 2;
        }

        public String pick(String a) {
            return "one:" + a;
        }

        public String pick(String a, String b) {
            return "two:" + a + b;
        }

        public long countMatching(Predicate<String> test) {
            return tags.stream().filter(test).count();
        }

        public String secret() {
            return secret;
        }

        public int resetCount;

        public void reset() {
            resetCount++;
        }
    }

    public record Point(int x, int y) {
        public int sum() {
            return x + y;
        }
    }
}
