package dev.isira.jaspring.signals;

/// Angular's NG0950: a required input was read before the parent template provided a value.
public final class RequiredInputException extends IllegalStateException {

    public RequiredInputException(String name) {
        super("Input '" + name + "' is required but no value is available yet");
    }
}
