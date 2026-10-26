package dev.isira.jaspring.template.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    @Test
    void keepsAllowlistedMarkupOnly() {
        assertEquals("<p class=\"x\">hi <b>there</b></p>", HtmlSanitizer.sanitize("<p class=\"x\" onclick=\"evil()\">hi <b>there</b></p>"));
        assertEquals("text", HtmlSanitizer.sanitize("<script>alert(1)</script><style>x</style><custom>text</custom>"));
        assertEquals("<div>kept</div>", HtmlSanitizer.sanitize("<form><div>kept</div></form>"));
    }

    @Test
    void sanitizesUrlAttributesAndEncodesText() {
        assertEquals("<a href=\"unsafe:javascript:x\">l</a>", HtmlSanitizer.sanitize("<a href=\"javascript:x\">l</a>"));
        assertEquals("<img src=\"https://a/b.png\" alt=\"&#34;q&#34;\">", HtmlSanitizer.sanitize("<img src='https://a/b.png' alt='\"q\"'>"));
        assertEquals("a &amp;&lt; b &#233;", HtmlSanitizer.sanitize("a &amp;&lt; b &eacute;".replace("&eacute;", "é")));
        assertEquals("&lt;not a tag", HtmlSanitizer.sanitize("<not a tag"));
    }

    @Test
    void closesUnbalancedTagsAndDropsComments() {
        assertEquals("<p>a<b>b</b></p>", HtmlSanitizer.sanitize("<p>a<b>b<!-- c --></p>"));
        assertEquals("<ul><li>x</li></ul>", HtmlSanitizer.sanitize("<ul><li>x</ul>"));
    }
}
