package dev.isira.jaspring.ide;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;

import java.beans.Introspector;
import java.util.ArrayList;

public final class JaspringReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), new PsiReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
                final var literal = (PsiLiteralExpression) element;
                final var pair = PsiTreeUtil.getParentOfType(literal, PsiNameValuePair.class);
                final var annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation.class);
                final var type = PsiTreeUtil.getParentOfType(literal, PsiClass.class);
                if (!(literal.getValue() instanceof String path) || pair == null || annotation == null || type == null
                        || pair.getValue() != literal
                        || !annotation.hasQualifiedName(JaspringProjectModel.COMPONENT)
                        || !(pair.getAttributeName().equals("templateUrl") || pair.getAttributeName().equals("styleUrl"))) return PsiReference.EMPTY_ARRAY;
                return new PsiReference[]{new PsiReferenceBase<>(literal, ElementManipulators.getValueTextRange(literal), false) {
                    @Override public PsiElement resolve() {
                        final var file = JaspringProjectModel.resource(type, path);
                        return file == null ? null : PsiManager.getInstance(element.getProject()).findFile(file);
                    }
                    @Override public PsiElement handleElementRename(String newName) {
                        final var slash = path.lastIndexOf('/');
                        return super.handleElementRename(path.substring(0, slash + 1) + newName);
                    }
                }};
            }
        });
        registrar.registerReferenceProvider(PlatformPatterns.or(PlatformPatterns.psiElement(XmlToken.class).withElementType(XmlTokenType.XML_DATA_CHARACTERS),
                PlatformPatterns.psiElement(XmlAttributeValue.class), PlatformPatterns.psiElement(XmlAttribute.class)), new PsiReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
                final var file = element.getContainingFile();
                if (JaspringProjectModel.owners(file).isEmpty()) return PsiReference.EMPTY_ARRAY;
                final var result = new ArrayList<PsiReference>();
                if (element instanceof XmlAttribute attribute) {
                    final var binding = BindingName.parse(attribute.getName());
                    for (final var candidate : TemplateBindings.forTag(attribute.getParent())) {
                        if (candidate.name().equals(binding.name()) && (candidate.kind() == binding.kind()
                                || binding.kind() == BindingName.Kind.REGULAR && candidate.kind() == BindingName.Kind.PROPERTY)) {
                            final var range = new TextRange(binding.offset(), binding.offset() + binding.name().length());
                            final var modelOutput = candidate.kind() == BindingName.Kind.EVENT
                                    && InheritanceUtil.isInheritor(candidate.field().getType(), "dev.isira.jaspring.signals.ModelSignal")
                                    && candidate.name().equals(candidate.field().getName() + "Change");
                            final var implicitName = candidate.name().equals(candidate.field().getName()) || modelOutput;
                            result.add(new PsiReferenceBase<>(attribute, range, true) {
                                @Override public PsiElement resolve() { return candidate.field(); }
                                @Override public PsiElement handleElementRename(String newName) {
                                    if (!implicitName) return attribute;
                                    final var name = attribute.getName();
                                    return attribute.setName(name.substring(0, range.getStartOffset()) + newName
                                            + (modelOutput ? "Change" : "") + name.substring(range.getEndOffset()));
                                }
                            });
                        }
                    }
                    return result.toArray(PsiReference[]::new);
                }
                for (final var expression : TemplateSyntax.of(file).expressions()) {
                    if (expression.range().getStartOffset() > element.getTextRange().getEndOffset()) break;
                    if (!expression.range().intersects(element.getTextRange())) continue;
                    for (var i = 0; i < expression.tokens().size(); i++) {
                        final var token = expression.tokens().get(i);
                        if (!token.identifier() || !element.getTextRange().contains(token.range()) || isDeclaration(file, token.range())) continue;
                        result.add(new MemberReference(element, token.range().shiftLeft(element.getTextRange().getStartOffset()), expression, i));
                    }
                }
                return result.toArray(PsiReference[]::new);
            }
        });
    }

    private static boolean isDeclaration(PsiFile file, TextRange range) {
        return TemplateSyntax.of(file).locals().stream().anyMatch(l -> l.declaration().equals(range));
    }

    private static final class MemberReference extends PsiReferenceBase<PsiElement> {
        private final TemplateSyntax.Expression expression;
        private final int index;

        private MemberReference(PsiElement host, TextRange range, TemplateSyntax.Expression expression, int index) {
            super(host, range, true);
            this.expression = expression;
            this.index = index;
        }
        @Override public PsiElement resolve() {
            final var symbol = JavaTemplateSymbols.resolve(myElement.getContainingFile(), expression, index);
            return symbol == null ? null : symbol.target();
        }
        @Override public PsiElement handleElementRename(String newName) {
            final var target = resolve();
            if (target instanceof PsiMethod method && !method.getName().equals(getCanonicalText())) {
                if (newName.startsWith("get") && newName.length() > 3) newName = Introspector.decapitalize(newName.substring(3));
                else if (newName.startsWith("is") && newName.length() > 2) newName = Introspector.decapitalize(newName.substring(2));
            }
            if (myElement instanceof XmlToken && myElement.getNode() instanceof LeafElement leaf) {
                final var text = myElement.getText();
                final var range = getRangeInElement();
                return leaf.replaceWithText(text.substring(0, range.getStartOffset()) + newName + text.substring(range.getEndOffset())).getPsi();
            }
            return super.handleElementRename(newName);
        }
    }
}
