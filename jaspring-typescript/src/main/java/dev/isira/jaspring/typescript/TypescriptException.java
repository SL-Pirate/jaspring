package dev.isira.jaspring.typescript;

/// The compiler host failed: the bundle crashed, Node is missing or exited abnormally. Type errors are not
/// exceptions; they come back as diagnostics in a [CompileResult].
public class TypescriptException extends RuntimeException {

    public TypescriptException(String message) {
        super(message);
    }

    public TypescriptException(String message, Throwable cause) {
        super(message, cause);
    }
}
