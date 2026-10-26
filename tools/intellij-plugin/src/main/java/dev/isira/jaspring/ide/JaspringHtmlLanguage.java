package dev.isira.jaspring.ide;

import com.intellij.lang.html.HTMLLanguage;

public final class JaspringHtmlLanguage extends HTMLLanguage {
    public static final JaspringHtmlLanguage INSTANCE = new JaspringHtmlLanguage();

    private JaspringHtmlLanguage() {
        super(HTMLLanguage.INSTANCE, "JaspringHTML");
    }
}
