package dev.isira.jaspring.template.eval;

/// A callable template value: an arrow function, or a Java method/shim bound to its receiver.
@FunctionalInterface
public interface JsFunction {

    Object call(Object... args);
}
