package dev.isira.jaspring.template.expr;

/// Position of an expression: `start`/`end` relative to the expression source, `absoluteStart`/`absoluteEnd` in the template.
public record ExprSpan(int start, int end, int absoluteStart, int absoluteEnd) {

    public static final ExprSpan NONE = new ExprSpan(0, 0, 0, 0);
}
