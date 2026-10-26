package dev.isira.jaspring.ide;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;

import java.util.Set;

public final class JaspringElementDescriptorProvider implements XmlElementDescriptorProvider {
    @Override
    public XmlElementDescriptor getDescriptor(XmlTag tag) {
        if (JaspringProjectModel.owners(tag.getContainingFile()).isEmpty()) return null;
        for (final var declaration : JaspringProjectModel.visibleDeclarations(tag.getContainingFile())) {
            if (declaration.isComponent() && TemplateBindings.matches(declaration.selector(), tag)) {
                return new Descriptor(tag.getName(), declaration.annotation().findAttributeValue("selector"));
            }
        }
        return Set.of("ng-container", "ng-template", "ng-content").contains(tag.getName())
                ? new Descriptor(tag.getName(), null) : null;
    }

    private static final class Descriptor extends AnyXmlElementDescriptor {
        private final String name;
        private final PsiElement declaration;

        private Descriptor(String name, PsiElement declaration) {
            super(null, null);
            this.name = name;
            this.declaration = declaration;
        }
        @Override public String getName() { return name; }
        @Override public String getName(PsiElement context) { return name; }
        @Override public String getQualifiedName() { return name; }
        @Override public String getDefaultName() { return name; }
        @Override public PsiElement getDeclaration() { return declaration; }
        @Override public XmlAttributeDescriptor[] getAttributesDescriptors(XmlTag context) {
            return new JaspringAttributeDescriptorsProvider().getAttributeDescriptors(context);
        }
    }
}
