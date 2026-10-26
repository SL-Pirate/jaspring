package dev.isira.jaspring.ide;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TemplateBindings {
    private static final Pattern SELECTOR = Pattern.compile("^([\\w-]+)?((?:\\[[\\w-]+(?:=[^]]+)?])*)$");
    private static final Pattern ATTRIBUTE = Pattern.compile("\\[([\\w-]+)(?:=([^]]+))?]");
    record Binding(String name, PsiField field, BindingName.Kind kind, boolean required) {}

    private TemplateBindings() {}

    static boolean matches(String selector, XmlTag tag) {
        for (final var candidate : selector.split(",")) {
            final var match = SELECTOR.matcher(candidate.trim());
            if (!match.matches()) continue;
            if (match.group(1) != null && !match.group(1).equals(tag.getName())) continue;
            final var attributes = ATTRIBUTE.matcher(match.group(2));
            var accepted = true;
            while (attributes.find()) {
                final var name = attributes.group(1);
                final var value = attributes.group(2);
                var found = false;
                for (final var attribute : tag.getAttributes()) {
                    if (BindingName.parse(attribute.getName()).name().equals(name)
                            && (value == null || value.replaceAll("^[\"']|[\"']$", "").equals(attribute.getValue()))) found = true;
                }
                if (!found) { accepted = false; break; }
            }
            if (accepted) return true;
        }
        return false;
    }

    static List<Binding> bindings(PsiClass type) {
        final var result = new ArrayList<Binding>();
        for (final var field : type.getAllFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            final var input = field.getAnnotation(JaspringProjectModel.INPUT);
            final var output = field.getAnnotation(JaspringProjectModel.OUTPUT);
            final var inputSignal = InheritanceUtil.isInheritor(field.getType(), "dev.isira.jaspring.signals.InputSignal");
            final var model = InheritanceUtil.isInheritor(field.getType(), "dev.isira.jaspring.signals.ModelSignal");
            if (input != null || inputSignal || model) {
                final var alias = input == null ? "" : JaspringProjectModel.stringValue(input, "value");
                final var name = alias.isEmpty() ? field.getName() : alias;
                final var required = input != null && Boolean.TRUE.equals(JavaPsiFacade.getInstance(type.getProject())
                        .getConstantEvaluationHelper().computeConstantExpression(input.findAttributeValue("required")));
                result.add(new Binding(name, field, BindingName.Kind.PROPERTY, required));
                if (model) {
                    result.add(new Binding(name, field, BindingName.Kind.TWO_WAY, false));
                    result.add(new Binding(name + "Change", field, BindingName.Kind.EVENT, false));
                }
            } else if (output != null || InheritanceUtil.isInheritor(field.getType(), "dev.isira.jaspring.signals.OutputRef")) {
                final var alias = output == null ? "" : JaspringProjectModel.stringValue(output, "value");
                result.add(new Binding(alias.isEmpty() ? field.getName() : alias, field, BindingName.Kind.EVENT, false));
            }
        }
        return List.copyOf(result);
    }

    static List<Binding> forTag(XmlTag tag) {
        return JaspringProjectModel.visibleDeclarations(tag.getContainingFile()).stream().filter(d -> matches(d.selector(), tag))
                .flatMap(d -> bindings(d.type()).stream()).toList();
    }
}
