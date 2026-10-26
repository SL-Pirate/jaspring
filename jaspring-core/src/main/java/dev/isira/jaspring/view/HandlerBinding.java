package dev.isira.jaspring.view;

import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.Scope;

/// One rendered event binding: the handler expression and the scope it was rendered in (loop variables included).
public record HandlerBinding(TemplateNode.BoundEvent event, Scope scope) {}
