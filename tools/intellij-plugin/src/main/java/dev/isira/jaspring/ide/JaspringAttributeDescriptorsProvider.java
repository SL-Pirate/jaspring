package dev.isira.jaspring.ide;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptorsProvider;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;

import java.util.LinkedHashSet;
import java.util.List;

public final class JaspringAttributeDescriptorsProvider implements XmlAttributeDescriptorsProvider {
    @Override
    public XmlAttributeDescriptor[] getAttributeDescriptors(XmlTag tag) {
        if (JaspringProjectModel.owners(tag.getContainingFile()).isEmpty()) return XmlAttributeDescriptor.EMPTY;
        final var names = new LinkedHashSet<>(List.of("[ngClass]", "[ngStyle]", "[(ngModel)]", "*ngIf", "*ngFor",
                "[ngSwitch]", "*ngSwitchCase", "*ngSwitchDefault", "*ngTemplateOutlet"));
        for (final var binding : TemplateBindings.forTag(tag)) {
            names.add(switch (binding.kind()) {
                case EVENT -> "(" + binding.name() + ")";
                case TWO_WAY -> "[(" + binding.name() + ")]";
                default -> "[" + binding.name() + "]";
            });
            if (binding.kind() == BindingName.Kind.PROPERTY) names.add(binding.name());
        }
        return names.stream().map(AnyXmlAttributeDescriptor::new).toArray(XmlAttributeDescriptor[]::new);
    }

    @Override
    public XmlAttributeDescriptor getAttributeDescriptor(String name, XmlTag tag) {
        if (JaspringProjectModel.owners(tag.getContainingFile()).isEmpty()) return null;
        if (BindingName.parse(name).kind() != BindingName.Kind.REGULAR) return new AnyXmlAttributeDescriptor(name);
        for (final var binding : TemplateBindings.forTag(tag)) {
            if (binding.name().equals(name)) return new AnyXmlAttributeDescriptor(name);
        }
        return null;
    }
}
