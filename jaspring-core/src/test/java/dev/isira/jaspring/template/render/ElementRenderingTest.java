package dev.isira.jaspring.template.render;

import static dev.isira.jaspring.template.render.RenderFixture.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.template.security.DomSanitizer;
import dev.isira.jaspring.template.security.SafeValue;

class ElementRenderingTest {

    public static final class Model {
        public String msg = "<b>&\"";
        public String name = "Ann";
        public boolean off;
        public boolean on = true;
        public Object nothing;
        public String html = "<script>x</script><b onclick=\"y\">bold</b><a href=\"javascript:alert(1)\">l</a>";
        public SafeValue.SafeHtml trusted = DomSanitizer.INSTANCE.bypassSecurityTrustHtml("<i x=\"1\">i</i>");
        public SafeValue.SafeResourceUrl frame = DomSanitizer.INSTANCE.bypassSecurityTrustResourceUrl("https://x/y");
        public Map<String, Boolean> classes = Map.of("d", true, "e", false);
        public List<String> tags = List.of("t1", "t2");
    }

    private final Model model = new Model();

    @Test
    void escapesTextAndInterpolation() {
        assertEquals("<p>&lt;b&gt;&amp;\"</p>", render("<p>{{ msg }}</p>", model));
        assertEquals("<p>a  b</p>", render("<p>a {{ nothing }} b</p>", model));
    }

    @Test
    void rendersStaticAttributesAndVoidElements() {
        assertEquals("<input type=\"text\" disabled=\"\">", render("<input type=\"text\" disabled>", model));
        assertEquals("<br><app-x></app-x>", render("<br/><app-x/>", model));
        assertEquals("<p title=\"a &quot;b&quot;\"></p>", render("<p title='a \"b\"'></p>", model));
    }

    @Test
    void reflectsPropertyBindingsToAttributes() {
        assertEquals("<input value=\"Ann\">", render("<input [value]=\"name\" [disabled]=\"off\">", model));
        assertEquals("<input disabled=\"\" tabindex=\"3\">", render("<input [disabled]=\"on\" [tabIndex]=\"3\">", model));
        assertEquals("<label for=\"x\"></label>", render("<label [htmlFor]=\"'x'\"></label>", model));
        assertEquals("<p title=\"Ann 1\"></p>", render("<p title=\"{{ name }} {{ 1 }}\"></p>", model));
    }

    @Test
    void attributeBindingsDropNullishValues() {
        assertEquals("<div aria-label=\"Ann\"></div>", render("<div [attr.aria-label]=\"name\" [attr.data-x]=\"nothing\"></div>", model));
    }

    @Test
    void mergesClassSources() {
        assertEquals("<div class=\"b c d\"></div>",
                render("<div class=\"a b\" [class.c]=\"on\" [class.a]=\"off\" [ngClass]=\"classes\"></div>", model));
        assertEquals("<div class=\"x y\"></div>", render("<div [class]=\"'x y'\"></div>", model));
        assertEquals("<div class=\"t1 t2\"></div>", render("<div [ngClass]=\"tags\"></div>", model));
    }

    @Test
    void mergesStyleSources() {
        assertEquals("<div style=\"color: red; width: 10px; font-size: 12px; margin-top: 2px\"></div>",
                render("<div style=\"color: red\" [style.width.px]=\"10\" [style.fontSize]=\"'12px'\" [ngStyle]=\"{'margin-top.px': 2}\"></div>", model));
        assertEquals("<div style=\"a: b\"></div>", render("<div [style]=\"'a: b'\"></div>", model));
        assertEquals("<div></div>", render("<div [style.width.px]=\"nothing\"></div>", model));
    }

    @Test
    void sanitizesInnerHtmlUnlessTrusted() {
        assertEquals("<div><b>bold</b><a href=\"unsafe:javascript:alert(1)\">l</a></div>", render("<div [innerHTML]=\"html\"></div>", model));
        assertEquals("<div><i x=\"1\">i</i></div>", render("<div [innerHTML]=\"trusted\"></div>", model));
        assertEquals("<div>&lt;b&gt;</div>", render("<div [textContent]=\"'<b>'\">ignored</div>", model));
    }

    @Test
    void sanitizesUrlsByContext() {
        assertEquals("<a href=\"unsafe:javascript:x\"></a>", render("<a [href]=\"'javascript:x'\"></a>", model));
        assertEquals("<a href=\"/ok?x=1\"></a>", render("<a [href]=\"'/ok?x=1'\"></a>", model));
        assertEquals("<iframe src=\"https://x/y\"></iframe>", render("<iframe [src]=\"frame\"></iframe>", model));
        assertThrows(IllegalArgumentException.class, () -> render("<iframe [src]=\"'https://x'\"></iframe>", model));
    }

    @Test
    void ngContainerRendersOnlyChildren() {
        assertEquals("<p>a</p>b", render("<ng-container><p>a</p>b</ng-container>", model));
    }

    @Test
    void stripsAngularNamespacePrefixes() {
        assertEquals("<svg><circle r=\"1\"></circle><use xlink:href=\"#a\"></use></svg>",
                render("<svg><circle r=\"1\"/><use [attr.xlink:href]=\"'#a'\"></use></svg>", model));
    }

    @Test
    void letDeclarationsAndReferencesAreVisibleToLaterSiblings() {
        assertEquals("<input name=\"n\">input:n", render("<input #inp name=\"n\">{{ inp.tagName }}:{{ inp.attributes.name }}", model));
        assertEquals("<div><span></span></div>span", render("<div><span #s></span></div>{{ s.tagName }}", model));
        assertEquals("<p>ANN</p>", render("@let upper = name.toUpperCase(); <p>{{ upper }}</p>", model));
    }

    @Test
    void eventBindingsRenderMarkersOnlyWhenTheHostRegistersThem() {
        final var host = new RenderFixture.RecordingHost();
        assertEquals("<form data-jsp-on=\"submit:1.1 keyup.enter:1.2:pd click:1.3 window:resize:1.4:sp\"></form>",
                render("<form (submit)=\"save()\" (keyup.enter)=\"go(); false\" (click)=\"n = 1\" (window:resize)=\"$event.stopPropagation()\"></form>", model, host));
        assertEquals(List.of("submit", "keyup.enter", "click", "resize"), host.handlers);
        assertEquals("<form></form>", render("<form (submit)=\"save()\"></form>", model));
    }

    @Test
    void contentAttributeScopesEveryElement() {
        final var host = new RenderFixture.RecordingHost();
        host.contentAttribute = "_ngcontent-jsp-c0";
        assertEquals("<div _ngcontent-jsp-c0=\"\"><span _ngcontent-jsp-c0=\"\">x</span></div>", render("<div><span>x</span></div>", model, host));
    }
}
