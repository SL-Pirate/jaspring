package dev.isira.jaspring.ide;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// Tolerant source ranges over HTML. It deliberately leaves validation to the runtime's Angular compiler.
/// All offsets refer to the original source, including entity escapes and unfinished expressions.
final class TemplateSyntax {
    private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "undefined", "of", "let", "as",
            "track", "when", "on", "idle", "immediate", "timer", "viewport", "interaction", "hover", "prefetch", "hydrate");
    private static final Pattern FOR = Pattern.compile("^\\s*([\\w$]+)\\s+of\\s+(.+?)(?:;|$)", Pattern.DOTALL);
    private static final Pattern ALIAS = Pattern.compile("(?:;\\s*as\\s+|let\\s+)([\\w$]+)(?:\\s*=\\s*([\\w$]+))?");
    record Token(String text, TextRange range, boolean identifier) {}
    record Expression(TextRange range, boolean event, List<Token> tokens) {}
    record Local(String name, TextRange declaration, TextRange initializer, boolean iterable, int start, int end) {}
    record Block(String name, TextRange range) {}
    record Document(List<Expression> expressions, List<Local> locals, List<Block> blocks) {
        Expression expressionAt(int offset) {
            return expressions.stream().filter(e -> e.range().getStartOffset() <= offset && offset <= e.range().getEndOffset())
                    .findFirst().orElse(null);
        }
        List<Local> localsAt(int offset) {
            return locals.stream().filter(l -> l.start() <= offset && offset < l.end())
                    .sorted((a, b) -> Integer.compare(b.start(), a.start())).toList();
        }
    }

    private TemplateSyntax() {}

    static Document of(PsiFile file) {
        return CachedValuesManager.getCachedValue(file,
                () -> CachedValueProvider.Result.create(parse(file.getText()), file));
    }

    static Document parse(String source) {
        final var expressions = new ArrayList<Expression>();
        final var locals = new ArrayList<Local>();
        final var blocks = new ArrayList<Block>();
        final var scopeEnds = new ArrayList<Integer>();
        for (var i = 0; i < source.length();) {
            while (!scopeEnds.isEmpty() && i >= scopeEnds.getLast()) scopeEnds.removeLast();
            if (source.startsWith("<!--", i)) {
                final var end = source.indexOf("-->", i + 4);
                i = end < 0 ? source.length() : end + 3;
            } else if (source.charAt(i) == '<' && i + 1 < source.length()
                    && (Character.isLetter(source.charAt(i + 1)) || source.charAt(i + 1) == '/' || source.charAt(i + 1) == '!')) {
                final var end = tagEnd(source, i);
                parseAttributes(source, i, end, expressions);
                final var tag = source.substring(i, Math.min(end, source.length()));
                if (tag.matches("(?is)<(?:script|style)(?:\\s.*|>.*)")) {
                    final var close = source.toLowerCase(java.util.Locale.ROOT).indexOf(tag.regionMatches(true, 1, "script", 0, 6)
                            ? "</script" : "</style", end);
                    i = close < 0 ? source.length() : tagEnd(source, close);
                } else i = end;
            } else if (source.startsWith("{{", i)) {
                final var end = expressionEnd(source, i + 2, "}}");
                addExpression(source, i + 2, end, false, expressions);
                i = Math.min(source.length(), end + 2);
            } else if (source.charAt(i) == '@' && i + 1 < source.length() && Character.isLetter(source.charAt(i + 1))) {
                var nameEnd = i + 1;
                while (nameEnd < source.length() && Character.isLetter(source.charAt(nameEnd))) nameEnd++;
                final var name = source.substring(i + 1, nameEnd);
                if (!Set.of("if", "else", "for", "empty", "switch", "case", "default", "defer", "placeholder", "loading", "error", "let").contains(name)) {
                    i = nameEnd;
                    continue;
                }
                blocks.add(new Block(name, new TextRange(i, nameEnd)));
                var next = whitespaceEnd(source, nameEnd);
                if (name.equals("else") && source.startsWith("if", next)
                        && next + 2 < source.length() && !Character.isJavaIdentifierPart(source.charAt(next + 2))) {
                    blocks.add(new Block("if", new TextRange(next, next + 2)));
                    next = whitespaceEnd(source, next + 2);
                }
                if (name.equals("let")) {
                    final var end = expressionEnd(source, next, ";");
                    final var equals = source.indexOf('=', next);
                    if (equals >= next && equals < end) {
                        final var localName = source.substring(next, equals).trim();
                        final var declarationStart = whitespaceEnd(source, next);
                        locals.add(new Local(localName, new TextRange(declarationStart, declarationStart + localName.length()),
                                new TextRange(equals + 1, end), false, end, scopeEnds.isEmpty() ? source.length() : scopeEnds.getLast()));
                        addExpression(source, equals + 1, end, false, expressions);
                    }
                    i = Math.min(source.length(), end + 1);
                    continue;
                }
                if (next < source.length() && source.charAt(next) == '(') {
                    final var headerStart = next + 1;
                    final var headerEnd = matching(source, next, '(', ')');
                    addExpression(source, headerStart, headerEnd, false, expressions);
                    next = whitespaceEnd(source, Math.min(source.length(), headerEnd + 1));
                    final var bodyEnd = next < source.length() && source.charAt(next) == '{'
                            ? matching(source, next, '{', '}') : source.length();
                    final var header = source.substring(headerStart, headerEnd);
                    final var loop = FOR.matcher(header);
                    if (name.equals("for") && loop.find()) {
                        locals.add(new Local(loop.group(1), new TextRange(headerStart + loop.start(1), headerStart + loop.end(1)),
                                new TextRange(headerStart + loop.start(2), headerStart + loop.end(2)), true,
                                headerStart + loop.end(2), bodyEnd));
                        for (final var builtIn : List.of("$index", "$count", "$first", "$last", "$even", "$odd")) {
                            locals.add(new Local(builtIn, new TextRange(headerStart, headerStart), null, false, headerStart, bodyEnd));
                        }
                    }
                    final var alias = ALIAS.matcher(header);
                    while (alias.find()) {
                        final var firstEnd = header.indexOf(';');
                        final var init = alias.group(2) == null
                                ? new TextRange(headerStart, firstEnd < 0 ? headerEnd : headerStart + firstEnd)
                                : new TextRange(headerStart + alias.start(2), headerStart + alias.end(2));
                        locals.add(new Local(alias.group(1), new TextRange(headerStart + alias.start(1), headerStart + alias.end(1)),
                                init, false, headerStart + alias.end(1), bodyEnd));
                    }
                }
                if (next < source.length() && source.charAt(next) == '{') {
                    scopeEnds.add(matching(source, next, '{', '}'));
                    i = next + 1;
                } else i = Math.max(nameEnd, next);
            } else i++;
        }
        return new Document(List.copyOf(expressions), List.copyOf(locals), List.copyOf(blocks));
    }

    private static void parseAttributes(String source, int start, int end, List<Expression> expressions) {
        var i = start + 1;
        while (i < end && !Character.isWhitespace(source.charAt(i)) && source.charAt(i) != '>') i++;
        while (i < end) {
            i = whitespaceEnd(source, i);
            final var nameStart = i;
            while (i < end && !Character.isWhitespace(source.charAt(i)) && "=>".indexOf(source.charAt(i)) < 0) i++;
            if (i == nameStart) { i++; continue; }
            final var binding = BindingName.parse(source.substring(nameStart, i));
            i = whitespaceEnd(source, i);
            if (i >= end || source.charAt(i) != '=') continue;
            i = whitespaceEnd(source, i + 1);
            if (i >= end) break;
            final var quote = source.charAt(i);
            final var quoted = quote == '\'' || quote == '"';
            final var valueStart = quoted ? ++i : i;
            while (i < end && (quoted ? source.charAt(i) != quote : !Character.isWhitespace(source.charAt(i)) && source.charAt(i) != '>')) i++;
            if (binding.hasExpression()) addExpression(source, valueStart, i, binding.kind() == BindingName.Kind.EVENT, expressions);
            else {
                var interpolation = source.indexOf("{{", valueStart);
                while (interpolation >= 0 && interpolation < i) {
                    final var close = Math.min(i, expressionEnd(source, interpolation + 2, "}}"));
                    addExpression(source, interpolation + 2, close, false, expressions);
                    interpolation = source.indexOf("{{", close + 2);
                }
            }
            if (quoted && i < end) i++;
        }
    }

    private static void addExpression(String source, int start, int end, boolean event, List<Expression> result) {
        result.add(new Expression(new TextRange(start, end), event, tokenize(source, start, end)));
    }

    static List<Token> tokenize(String source, int start, int end) {
        final var result = new ArrayList<Token>();
        for (var i = start; i < end;) {
            final var c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            final var tokenStart = i++;
            final var identifier = Character.isJavaIdentifierStart(c);
            if (c == '\'' || c == '"' || c == '`') i = Math.min(end, stringEnd(source, tokenStart));
            else if (identifier) while (i < end && Character.isJavaIdentifierPart(source.charAt(i))) i++;
            else if (Character.isDigit(c)) while (i < end && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
            else if (i < end && (source.startsWith("?.", tokenStart) || source.startsWith("||", tokenStart)
                    || source.startsWith("&&", tokenStart) || source.startsWith("=>", tokenStart))) i++;
            final var text = source.substring(tokenStart, i);
            result.add(new Token(text, new TextRange(tokenStart, i), identifier && !KEYWORDS.contains(text)));
        }
        return List.copyOf(result);
    }

    private static int whitespaceEnd(String source, int start) {
        var i = start;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) i++;
        return i;
    }

    private static int stringEnd(String source, int start) {
        final var quote = source.charAt(start);
        var i = start + 1;
        while (i < source.length()) {
            if (source.charAt(i) == '\\') i = Math.min(source.length(), i + 2);
            else if (source.charAt(i++) == quote) break;
        }
        return i;
    }

    private static int tagEnd(String source, int start) {
        var i = start + 1;
        while (i < source.length()) {
            final var c = source.charAt(i);
            if (c == '\'' || c == '"') i = stringEnd(source, i);
            else if (c == '>') return i + 1;
            else i++;
        }
        return source.length();
    }

    private static int matching(String source, int start, char open, char close) {
        var depth = 1;
        for (var i = start + 1; i < source.length(); i++) {
            final var c = source.charAt(i);
            if (c == '\'' || c == '"' || c == '`') i = stringEnd(source, i) - 1;
            else if (source.startsWith("<!--", i)) {
                final var end = source.indexOf("-->", i + 4);
                if (end < 0) return source.length();
                i = end + 2;
            } else if (open == '{' && c == '<' && i + 1 < source.length()
                    && (Character.isLetter(source.charAt(i + 1)) || source.charAt(i + 1) == '/')) i = tagEnd(source, i) - 1;
            else if (c == open) depth++;
            else if (c == close && --depth == 0) return i;
        }
        return source.length();
    }

    private static int expressionEnd(String source, int start, String terminator) {
        var depth = 0;
        for (var i = start; i < source.length(); i++) {
            final var c = source.charAt(i);
            if (depth == 0 && source.startsWith(terminator, i)) return i;
            if (c == '\'' || c == '"' || c == '`') i = stringEnd(source, i) - 1;
            else if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth = Math.max(0, depth - 1);
        }
        return source.length();
    }
}
