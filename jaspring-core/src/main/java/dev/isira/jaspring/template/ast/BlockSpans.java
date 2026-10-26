package dev.isira.jaspring.template.ast;

/// Spans shared by every `@block`: the name, the whole block, its opening and its closing brace.
public record BlockSpans(SourceSpan nameSpan, SourceSpan sourceSpan, SourceSpan startSourceSpan, SourceSpan endSourceSpan) {}
