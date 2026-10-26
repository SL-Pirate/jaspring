package dev.isira.jaspring.template.ast;

import dev.isira.jaspring.template.expr.Expr;

/// The `on ...` / `when ...` triggers of a `@defer` block.
public sealed interface DeferredTrigger {

    SourceSpan sourceSpan();

    record When(Expr value, SourceSpan sourceSpan) implements DeferredTrigger {}

    record Idle(SourceSpan sourceSpan) implements DeferredTrigger {}

    record Immediate(SourceSpan sourceSpan) implements DeferredTrigger {}

    record Hover(String reference, SourceSpan sourceSpan) implements DeferredTrigger {}

    record Timer(int delay, SourceSpan sourceSpan) implements DeferredTrigger {}

    record Interaction(String reference, SourceSpan sourceSpan) implements DeferredTrigger {}

    record Viewport(String reference, Expr.LiteralMap options, SourceSpan sourceSpan) implements DeferredTrigger {}

    record Never(SourceSpan sourceSpan) implements DeferredTrigger {}
}
