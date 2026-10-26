package dev.isira.jaspring.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.ComponentDefs;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;

class StyleProcessorTest {

    private static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();

    @Component(selector = "app-styled", template = "<p>x</p>", styles = {
            ":host { display: block }",
            ".a > .b, h1:hover::after { color: red }",
            ":host-context(.dark) .x { color: white }",
            ".c ::ng-deep .inner, .d /deep/ .e { margin: 0 }",
            "@media (min-width: 10px) { .m { top: 0 } } @keyframes spin { from { opacity: 0 } to { opacity: 1 } }"
    })
    public static class Styled {}

    @Component(selector = "app-file", template = "<p>x</p>", styleUrl = "style-processor-test.css")
    public static class FromFile {}

    @Test
    void scopesSelectorsLikeAngularsEmulatedEncapsulation() {
        final var css = new StyleProcessor(RUNTIME).process(ComponentDefs.of(Styled.class, 4));

        assertTrue(css.contains("[_nghost-jsp-c4] { display: block }"), css);
        assertTrue(css.contains(".a[_ngcontent-jsp-c4] > .b[_ngcontent-jsp-c4], h1[_ngcontent-jsp-c4]:hover::after { color: red }"), css);
        assertTrue(css.contains(".dark[_nghost-jsp-c4] .x[_ngcontent-jsp-c4], .dark [_nghost-jsp-c4] .x[_ngcontent-jsp-c4] { color: white }"), css);
        assertTrue(css.contains(".c[_ngcontent-jsp-c4] .inner, .d[_ngcontent-jsp-c4] .e { margin: 0 }"), css);
        assertTrue(css.contains(".m[_ngcontent-jsp-c4] { top: 0 }"), css);
        assertTrue(css.contains("from { opacity: 0 }"), "keyframe steps are not selectors: " + css);
    }

    @Test
    void styleUrlLoadsNextToTheComponent() {
        final var css = new StyleProcessor(RUNTIME).process(ComponentDefs.of(FromFile.class, 0));
        assertEquals("p[_ngcontent-jsp-c0] { color: blue }", css.trim());
    }
}
