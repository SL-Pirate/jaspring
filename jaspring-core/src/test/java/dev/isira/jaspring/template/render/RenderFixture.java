package dev.isira.jaspring.template.render;

import java.util.ArrayList;
import java.util.List;

import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.EvaluationContext;
import dev.isira.jaspring.template.eval.Scope;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;
import dev.isira.jaspring.template.parse.ParseOptions;

final class RenderFixture {

    static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();
    static final GraalAngularTemplateParser PARSER = new GraalAngularTemplateParser(RUNTIME);
    static final DomSchema SCHEMA = DomSchema.load(RUNTIME);

    private RenderFixture() {}

    static String render(String template, Object component) {
        return render(template, component, RenderHost.NONE);
    }

    static String render(String template, Object component, RenderHost host) {
        final var nodes = PARSER.parse(template, "test.html", ParseOptions.defaults()).nodes();
        return new TemplateRenderer(SCHEMA).render(nodes, EvaluationContext.of(component), host);
    }

    /// Hands out sequential handler refs and records what was registered.
    static final class RecordingHost implements RenderHost {
        final List<String> handlers = new ArrayList<>();
        String contentAttribute;

        @Override
        public String registerHandler(TemplateNode.BoundEvent event, Scope scope) {
            handlers.add(event.name());
            return "1." + handlers.size();
        }

        @Override
        public String contentAttribute() {
            return contentAttribute;
        }
    }
}
