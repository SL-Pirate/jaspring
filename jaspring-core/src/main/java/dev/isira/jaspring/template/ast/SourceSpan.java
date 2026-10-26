package dev.isira.jaspring.template.ast;

/// Where a node sits in its template: character offsets plus the line and column of the start (0-based, like Angular).
public record SourceSpan(String url, int start, int end, int line, int col) {

    public static final SourceSpan NONE = new SourceSpan("", 0, 0, 0, 0);

    public String textIn(String template) {
        return template.substring(start, end);
    }
}
