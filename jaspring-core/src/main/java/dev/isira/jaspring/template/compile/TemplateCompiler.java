package dev.isira.jaspring.template.compile;

import java.util.ArrayList;
import java.util.List;

import dev.isira.jaspring.component.ComponentDef;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.ParseOptions;
import dev.isira.jaspring.template.parse.TemplateCompileException;
import dev.isira.jaspring.template.parse.TemplateError;
import dev.isira.jaspring.template.parse.TemplateParser;

/// Parses a component's template with Angular's parser, then checks what Angular's type checker would check:
/// members exist on the component, elements and properties are known, pipes exist.
public final class TemplateCompiler {

    private final TemplateParser parser;
    private final DomSchema schema;

    public TemplateCompiler(TemplateParser parser, DomSchema schema) {
        this.parser = parser;
        this.schema = schema;
    }

    public CompiledTemplate compile(ComponentDef def, ComponentRegistry registry) {
        final var parsed = parser.parse(def.templateSource(), def.templateUrl(), new ParseOptions(def.preserveWhitespaces(), false));
        final var errors = new ArrayList<TemplateError>();
        new TemplateValidator(def, registry, schema, def.templateSource(), def.templateUrl(), errors).validate(parsed.nodes());
        if (!errors.isEmpty()) {
            throw new TemplateCompileException(List.copyOf(errors));
        }
        return new CompiledTemplate(parsed);
    }
}
