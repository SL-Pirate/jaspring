package dev.isira.jaspring.template.eval;

import dev.isira.jaspring.pipe.PipeRegistry;

/// Everything an expression can see: the component instance (implicit receiver), the local scope and the pipes.
public record EvaluationContext(Object component, Scope scope, PipeRegistry pipes) {

    public static EvaluationContext of(Object component) {
        return new EvaluationContext(component, Scope.root(), PipeRegistry.EMPTY);
    }

    public EvaluationContext withScope(Scope newScope) {
        return new EvaluationContext(component, newScope, pipes);
    }

    public EvaluationContext withPipes(PipeRegistry newPipes) {
        return new EvaluationContext(component, scope, newPipes);
    }
}
