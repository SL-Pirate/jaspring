package dev.isira.jaspring.template.render;

import java.util.List;

import dev.isira.jaspring.template.ast.TemplateNode;
import dev.isira.jaspring.template.eval.Scope;

/// What `#ref` on an `<ng-template>` resolves to: the nodes plus the scope they were declared in, so an
/// outlet elsewhere still sees the declaring view's locals. `variables` are the `let-x="key"` declarations.
public record TemplateRef(List<TemplateNode> nodes, List<TemplateNode.Variable> variables, Scope declarationScope) {}
