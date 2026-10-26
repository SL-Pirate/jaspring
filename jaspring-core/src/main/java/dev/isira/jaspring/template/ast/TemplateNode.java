package dev.isira.jaspring.template.ast;

import java.util.List;
import java.util.Map;

import dev.isira.jaspring.template.expr.Expr;

/// Angular's template AST (`render3/r3_ast.ts`) as Java records, one per class, same names and fields.
/// Selectorless `Component`/`Directive` nodes are not produced because that syntax is disabled.
public sealed interface TemplateNode {

    SourceSpan sourceSpan();

    /// Attributes allowed in `Template.templateAttrs`.
    sealed interface Attribute extends TemplateNode permits TextAttribute, BoundAttribute {
        String name();
    }

    record Text(String value, SourceSpan sourceSpan) implements TemplateNode {}

    record BoundText(Expr value, SourceSpan sourceSpan) implements TemplateNode {}

    record TextAttribute(String name, String value, SourceSpan sourceSpan, SourceSpan keySpan, SourceSpan valueSpan)
            implements Attribute {}

    record BoundAttribute(String name, BindingType type, SecurityContext securityContext, Expr value, String unit,
            SourceSpan sourceSpan, SourceSpan keySpan, SourceSpan valueSpan) implements Attribute {}

    record BoundEvent(String name, EventType type, Expr handler, String target, String phase, SourceSpan sourceSpan,
            SourceSpan handlerSpan, SourceSpan keySpan) implements TemplateNode {}

    record Element(String name, List<TextAttribute> attributes, List<BoundAttribute> inputs, List<BoundEvent> outputs,
            List<TemplateNode> children, List<Reference> references, boolean isSelfClosing, boolean isVoid,
            SourceSpan sourceSpan, SourceSpan startSourceSpan, SourceSpan endSourceSpan) implements TemplateNode {}

    /// An `<ng-template>` or the desugared form of a structural directive (`*ngIf`), whose attrs land in `templateAttrs`.
    record Template(String tagName, List<TextAttribute> attributes, List<BoundAttribute> inputs, List<BoundEvent> outputs,
            List<Attribute> templateAttrs, List<TemplateNode> children, List<Reference> references,
            List<Variable> variables, boolean isSelfClosing, SourceSpan sourceSpan, SourceSpan startSourceSpan,
            SourceSpan endSourceSpan) implements TemplateNode {}

    record Content(String selector, List<TextAttribute> attributes, List<TemplateNode> children, boolean isSelfClosing,
            SourceSpan sourceSpan, SourceSpan startSourceSpan, SourceSpan endSourceSpan) implements TemplateNode {}

    record Variable(String name, String value, SourceSpan sourceSpan, SourceSpan keySpan, SourceSpan valueSpan)
            implements TemplateNode {}

    record Reference(String name, String value, SourceSpan sourceSpan, SourceSpan keySpan, SourceSpan valueSpan)
            implements TemplateNode {}

    record LetDeclaration(String name, Expr value, SourceSpan sourceSpan, SourceSpan nameSpan, SourceSpan valueSpan)
            implements TemplateNode {}

    /// An ICU expansion. `message` holds the case structure; `vars` and `placeholders` hold the bound expressions.
    record Icu(Map<String, BoundText> vars, Map<String, TemplateNode> placeholders, I18nNode.Icu message,
            SourceSpan sourceSpan) implements TemplateNode {}

    record IfBlock(List<IfBlockBranch> branches, BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    /// `expression` is `null` for `@else`; `expressionAlias` holds the `as x` variable.
    record IfBlockBranch(Expr expression, List<TemplateNode> children, Variable expressionAlias, BlockSpans spans)
            implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record ForLoopBlock(Variable item, Expr expression, Expr trackBy, List<Variable> contextVariables,
            List<TemplateNode> children, ForLoopBlockEmpty empty, SourceSpan mainBlockSpan, BlockSpans spans)
            implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record ForLoopBlockEmpty(List<TemplateNode> children, BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record SwitchBlock(Expr expression, List<SwitchBlockCaseGroup> groups, List<UnknownBlock> unknownBlocks,
            SwitchExhaustiveCheck exhaustiveCheck, BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    /// Consecutive empty `@case` blocks share one group; `expression` is `null` for `@default`.
    record SwitchBlockCaseGroup(List<SwitchBlockCase> cases, List<TemplateNode> children, BlockSpans spans)
            implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record SwitchBlockCase(Expr expression, BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record SwitchExhaustiveCheck(BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record DeferredBlock(List<TemplateNode> children, List<DeferredTrigger> triggers, List<DeferredTrigger> prefetchTriggers,
            List<DeferredTrigger> hydrateTriggers, DeferredBlockPlaceholder placeholder, DeferredBlockLoading loading,
            DeferredBlockError error, SourceSpan mainBlockSpan, BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record DeferredBlockPlaceholder(List<TemplateNode> children, Integer minimumTime, BlockSpans spans)
            implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record DeferredBlockLoading(List<TemplateNode> children, Integer afterTime, Integer minimumTime, BlockSpans spans)
            implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record DeferredBlockError(List<TemplateNode> children, BlockSpans spans) implements TemplateNode {
        @Override
        public SourceSpan sourceSpan() {
            return spans.sourceSpan();
        }
    }

    record UnknownBlock(String name, SourceSpan sourceSpan, SourceSpan nameSpan) implements TemplateNode {}
}
