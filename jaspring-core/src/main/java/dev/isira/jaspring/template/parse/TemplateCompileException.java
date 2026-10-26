package dev.isira.jaspring.template.parse;

import java.util.List;
import java.util.stream.Collectors;

/// Thrown at startup when a template is not valid Angular, or binds things the component does not have.
public final class TemplateCompileException extends RuntimeException {

    private final List<TemplateError> errors;

    public TemplateCompileException(List<TemplateError> errors) {
        super(errors.stream().map(TemplateError::text).collect(Collectors.joining("\n")));
        this.errors = List.copyOf(errors);
    }

    public List<TemplateError> errors() {
        return errors;
    }
}
