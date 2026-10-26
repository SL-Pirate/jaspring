package dev.isira.jaspring.template.eval;

/// A template expression failed at evaluation time (the JavaScript equivalent would be a TypeError).
public final class EvaluationException extends RuntimeException {

    public EvaluationException(String message) {
        super(message);
    }

    public EvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
