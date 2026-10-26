package dev.isira.jaspring.template.parse;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Value;

import dev.isira.jaspring.template.ast.ParsedTemplate;

/// Parses with Angular's real compiler (in GraalJS) and converts the result to Java records before the context lock is released.
public final class GraalAngularTemplateParser implements TemplateParser {

    private final AngularCompilerRuntime runtime;

    public GraalAngularTemplateParser(AngularCompilerRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public ParsedTemplate parse(String template, String url, ParseOptions options) {
        return runtime.parse(template, url, options, result -> {
            final var errors = new ArrayList<TemplateError>();
            final var warnings = new ArrayList<TemplateError>();
            final var reported = result.getMember("errors");
            for (var i = 0L; i < reported.getArraySize(); i++) {
                final var error = convertError(reported.getArrayElement(i), url);
                (error.level() == TemplateError.Level.ERROR ? errors : warnings).add(error);
            }
            if (!errors.isEmpty() && !options.tolerateErrors()) {
                throw new TemplateCompileException(errors);
            }
            final var converter = new NodeConverter(url);
            final var nodes = converter.nodes(result.getMember("nodes"));
            final var selectors = new ArrayList<String>();
            final var ngContentSelectors = result.getMember("ngContentSelectors");
            for (var i = 0L; i < ngContentSelectors.getArraySize(); i++) {
                selectors.add(ngContentSelectors.getArrayElement(i).asString());
            }
            return new ParsedTemplate(List.copyOf(nodes), List.copyOf(selectors), url, List.copyOf(warnings));
        });
    }

    private static TemplateError convertError(Value error, String url) {
        final var start = error.getMember("start");
        final var end = error.getMember("end");
        return new TemplateError(
                error.getMember("message").asString(),
                error.getMember("text").asString(),
                url,
                start.getMember("line").asInt(),
                start.getMember("col").asInt(),
                start.getMember("offset").asInt(),
                end.getMember("offset").asInt(),
                TemplateError.Level.fromOrdinal(error.getMember("level").asInt()));
    }
}
