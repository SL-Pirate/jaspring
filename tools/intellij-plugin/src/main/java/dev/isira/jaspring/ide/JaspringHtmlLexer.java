package dev.isira.jaspring.ide;

import com.intellij.lexer.HtmlLexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;

/// Keeps comparisons and string literals inside expressions from becoming HTML tags.
/// The shadow buffer has identical offsets; PSI and references still see the original source.
final class JaspringHtmlLexer extends LexerBase {
    private final HtmlLexer delegate = new HtmlLexer();
    private CharSequence source = "";

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
        source = buffer;
        final var shadow = new StringBuilder(buffer);
        for (final var expression : TemplateSyntax.parse(buffer.toString()).expressions()) {
            for (var i = expression.range().getStartOffset(); i < expression.range().getEndOffset(); i++) {
                if (shadow.charAt(i) == '<' || shadow.charAt(i) == '>') shadow.setCharAt(i, ' ');
            }
        }
        delegate.start(shadow, startOffset, endOffset, initialState);
    }

    @Override public int getState() { return delegate.getState(); }
    @Override public IElementType getTokenType() { return delegate.getTokenType(); }
    @Override public int getTokenStart() { return delegate.getTokenStart(); }
    @Override public int getTokenEnd() { return delegate.getTokenEnd(); }
    @Override public void advance() { delegate.advance(); }
    @Override public CharSequence getBufferSequence() { return source; }
    @Override public int getBufferEnd() { return delegate.getBufferEnd(); }
}
