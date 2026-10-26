package dev.isira.jaspring.template.compile;

import dev.isira.jaspring.template.ast.ParsedTemplate;

/// A parsed and validated template, ready to render.
public record CompiledTemplate(ParsedTemplate parsed) {}
