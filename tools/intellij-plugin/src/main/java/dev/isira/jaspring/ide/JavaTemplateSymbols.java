package dev.isira.jaspring.ide;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class JavaTemplateSymbols {
    record Symbol(String name, PsiElement target, PsiType type, boolean method) {}

    private JavaTemplateSymbols() {}

    static List<Symbol> roots(PsiFile file, int offset) {
        final var result = new LinkedHashMap<String, Symbol>();
        final var syntax = TemplateSyntax.of(file);
        for (final var local : syntax.localsAt(offset)) {
            if (result.containsKey(local.name())) continue;
            var type = local.initializer() == null ? null : expressionType(file, local.initializer(), 0);
            if (local.iterable()) type = itemType(type);
            result.put(local.name(), new Symbol(local.name(), new LocalElement(file, local), type, false));
        }
        final var expression = syntax.expressionAt(offset);
        if (expression != null && expression.event()) result.put("$event", new Symbol("$event", null, null, false));
        for (final var owner : JaspringProjectModel.owners(file)) {
            final var type = JavaPsiFacade.getElementFactory(file.getProject()).createType(owner.type());
            for (final var symbol : members(type, true)) result.putIfAbsent(symbol.name(), symbol);
        }
        return List.copyOf(result.values());
    }

    static Symbol resolve(PsiFile file, TemplateSyntax.Expression expression, int tokenIndex) {
        return resolve(file, expression.tokens(), tokenIndex, 0);
    }

    private static Symbol resolve(PsiFile file, List<TemplateSyntax.Token> tokens, int index, int depth) {
        if (index < 0 || index >= tokens.size() || depth > 24) return null;
        final var token = tokens.get(index);
        if (!token.identifier()) return null;
        if (index > 0 && index + 1 < tokens.size() && tokens.get(index + 1).text().equals(":")
                && List.of("{", ",").contains(tokens.get(index - 1).text())) return null;
        if (isArrowLocal(tokens, index)) return null;
        final var call = index + 1 < tokens.size() && tokens.get(index + 1).text().equals("(");
        if (index > 0 && tokens.get(index - 1).text().equals("|")) {
            return JaspringProjectModel.visibleDeclarations(file).stream().filter(d -> d.pipeName().equals(token.text()))
                    .map(d -> new Symbol(token.text(), d.type(), null, false)).findFirst().orElse(null);
        }
        if (index > 1 && List.of(".", "?.").contains(tokens.get(index - 1).text())) {
            if (tokens.get(index - 2).text().equals("this")) {
                for (final var owner : JaspringProjectModel.owners(file)) {
                    final var type = JavaPsiFacade.getElementFactory(file.getProject()).createType(owner.type());
                    final var found = named(members(type, true), token.text(), call);
                    if (found != null) return found;
                }
                return null;
            }
            final var receiver = valueType(file, tokens, index - 2, depth + 1);
            return named(members(receiver, false), token.text(), call);
        }
        for (final var local : TemplateSyntax.of(file).localsAt(token.range().getStartOffset())) {
            if (!local.name().equals(token.text())) continue;
            var type = local.initializer() == null ? null : expressionType(file, local.initializer(), depth + 1);
            if (local.iterable()) type = itemType(type);
            return new Symbol(local.name(), new LocalElement(file, local), type, false);
        }
        for (final var owner : JaspringProjectModel.owners(file)) {
            final var type = JavaPsiFacade.getElementFactory(file.getProject()).createType(owner.type());
            final var found = named(members(type, true), token.text(), call);
            if (found != null) return found;
        }
        return null;
    }

    static List<Symbol> completion(PsiFile file, TemplateSyntax.Expression expression, int offset) {
        final var tokens = expression.tokens();
        var before = -1;
        for (var i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).range().getEndOffset() <= offset) before = i;
            else break;
        }
        if (before >= 0 && tokens.get(before).identifier()) before--;
        if (before >= 0 && List.of(".", "?.").contains(tokens.get(before).text())) {
            if (before > 0 && tokens.get(before - 1).text().equals("this")) {
                final var result = new ArrayList<Symbol>();
                for (final var owner : JaspringProjectModel.owners(file)) {
                    result.addAll(members(JavaPsiFacade.getElementFactory(file.getProject()).createType(owner.type()), true));
                }
                return List.copyOf(result);
            }
            return members(valueType(file, tokens, before - 1, 0), false);
        }
        return roots(file, offset);
    }

    private static PsiType expressionType(PsiFile file, TextRange range, int depth) {
        if (depth > 24) return null;
        final var tokens = TemplateSyntax.tokenize(file.getText(), range.getStartOffset(), range.getEndOffset());
        return tokens.isEmpty() ? null : valueType(file, tokens, tokens.size() - 1, depth + 1);
    }

    private static PsiType valueType(PsiFile file, List<TemplateSyntax.Token> tokens, int index, int depth) {
        if (index < 0 || depth > 24) return null;
        final var token = tokens.get(index);
        if (token.text().equals(")") || token.text().equals("]")) {
            final var open = token.text().equals(")") ? "(" : "[";
            var nesting = 1;
            var start = index - 1;
            while (start >= 0) {
                if (tokens.get(start).text().equals(token.text())) nesting++;
                else if (tokens.get(start).text().equals(open) && --nesting == 0) break;
                start--;
            }
            if (start <= 0) return null;
            final var type = valueType(file, tokens, start - 1, depth + 1);
            if (open.equals("[")) return itemType(type);
            final var callee = resolve(file, tokens, start - 1, depth + 1);
            return callee != null && callee.method() ? type : signalValue(type);
        }
        if (token.text().startsWith("\"") || token.text().startsWith("'")) {
            return PsiType.getJavaLangString(file.getManager(), file.getResolveScope());
        }
        final var symbol = resolve(file, tokens, index, depth + 1);
        return symbol == null ? null : symbol.type();
    }

    static List<Symbol> members(PsiType type, boolean component) {
        if (!(type instanceof PsiClassType classType)) return List.of();
        final var resolved = classType.resolveGenerics();
        final var javaClass = resolved.getElement();
        if (javaClass == null) return List.of();
        final var result = new LinkedHashMap<String, Symbol>();
        for (final var field : javaClass.getAllFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || (!component && !field.hasModifierProperty(PsiModifier.PUBLIC))) continue;
            final var substitutor = TypeConversionUtil.getSuperClassSubstitutor(field.getContainingClass(), javaClass, resolved.getSubstitutor());
            result.putIfAbsent(field.getName(), new Symbol(field.getName(), field, substitutor.substitute(field.getType()), false));
        }
        for (final var method : javaClass.getAllMethods()) {
            if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)
                    || method.getContainingClass() == null || "java.lang.Object".equals(method.getContainingClass().getQualifiedName())) continue;
            final var substitutor = TypeConversionUtil.getSuperClassSubstitutor(method.getContainingClass(), javaClass, resolved.getSubstitutor());
            final var returnType = substitutor.substitute(method.getReturnType());
            final var symbol = new Symbol(method.getName(), method, returnType, true);
            result.putIfAbsent(method.getName() + "()", symbol);
            if (method.getParameterList().isEmpty()) {
                final var prefix = method.getName().startsWith("get") ? 3 : method.getName().startsWith("is") ? 2 : 0;
                if (prefix > 0 && method.getName().length() > prefix) {
                    final var name = Introspector.decapitalize(method.getName().substring(prefix));
                    result.putIfAbsent(name, new Symbol(name, method, returnType, false));
                }
            }
        }
        // Template JavaScript conveniences are runtime shims, not Java declarations to rename.
        if (InheritanceUtil.isInheritor(type, "java.lang.CharSequence") || InheritanceUtil.isInheritor(type, "java.util.Collection")) {
            for (final var name : List.of("length", "includes", "slice", "indexOf")) {
                result.putIfAbsent(name, new Symbol(name, null, null, !name.equals("length")));
            }
        }
        return List.copyOf(result.values());
    }

    private static Symbol named(List<Symbol> symbols, String name, boolean call) {
        if (call) {
            final var method = symbols.stream().filter(s -> s.name().equals(name) && s.method()).findFirst().orElse(null);
            if (method != null) return method;
        }
        return symbols.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
    }

    // Until arrow parameter types are inferred, keep lexical locals out of Java rename searches.
    private static boolean isArrowLocal(List<TemplateSyntax.Token> tokens, int index) {
        if (index > 0 && List.of(".", "?.").contains(tokens.get(index - 1).text())) return false;
        for (var arrow = 1; arrow < tokens.size(); arrow++) {
            if (!tokens.get(arrow).text().equals("=>")) continue;
            var parameterStart = arrow - 1;
            if (tokens.get(parameterStart).text().equals(")")) {
                while (parameterStart > 0 && !tokens.get(parameterStart).text().equals("(")) parameterStart--;
            }
            var shadows = false;
            for (var parameter = parameterStart; parameter < arrow; parameter++) {
                if (tokens.get(parameter).identifier() && tokens.get(parameter).text().equals(tokens.get(index).text())) shadows = true;
            }
            if (!shadows) continue;
            if (index >= parameterStart && index < arrow) return true;
            var depth = 0;
            var end = arrow + 1;
            for (; end < tokens.size(); end++) {
                final var text = tokens.get(end).text();
                if (depth == 0 && List.of(")", "]", "}", ",", ";").contains(text)) break;
                if (List.of("(", "[", "{").contains(text)) depth++;
                else if (List.of(")", "]", "}").contains(text)) depth--;
            }
            if (index > arrow && index < end) return true;
        }
        return false;
    }

    static PsiType signalValue(PsiType type) {
        return PsiUtil.substituteTypeParameter(type, "dev.isira.jaspring.signals.Signal", 0, false);
    }

    static PsiType itemType(PsiType type) {
        if (type instanceof PsiArrayType array) return array.getComponentType();
        return PsiUtil.substituteTypeParameter(type, "java.lang.Iterable", 0, false);
    }

    private static final class LocalElement extends FakePsiElement implements PsiNamedElement {
        private final PsiFile file;
        private final TemplateSyntax.Local local;

        private LocalElement(PsiFile file, TemplateSyntax.Local local) { this.file = file; this.local = local; }
        @Override public String getName() { return local.name(); }
        @Override public PsiElement getParent() { return file; }
        @Override public TextRange getTextRange() { return local.declaration(); }
        @Override public int getTextOffset() { return local.declaration().getStartOffset(); }
        @Override public PsiElement getNavigationElement() {
            final var element = file.findElementAt(getTextOffset());
            return element == null ? file : element;
        }
        @Override public PsiElement setName(String name) {
            final var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document != null && !local.declaration().isEmpty()) {
                document.replaceString(local.declaration().getStartOffset(), local.declaration().getEndOffset(), name);
                PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
            }
            return this;
        }
    }
}
