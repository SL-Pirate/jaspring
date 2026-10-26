package dev.isira.jaspring.ide;

import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.xml.HtmlFileElementType;

public final class JaspringHtmlParserDefinition extends HTMLParserDefinition {
    private static final IFileElementType FILE = new HtmlFileElementType("JASPRING_HTML_FILE", JaspringHtmlLanguage.INSTANCE);

    @Override
    public Lexer createLexer(Project project) { return new JaspringHtmlLexer(); }

    @Override
    public IFileElementType getFileNodeType() { return FILE; }

    @Override
    public PsiFile createFile(FileViewProvider provider) { return new HtmlFileImpl(provider, FILE); }
}
