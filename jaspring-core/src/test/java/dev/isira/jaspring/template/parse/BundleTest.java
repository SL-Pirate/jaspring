package dev.isira.jaspring.template.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BundleTest {

    private static AngularCompilerRuntime runtime;

    @BeforeAll
    static void load() {
        runtime = AngularCompilerRuntime.load();
    }

    @AfterAll
    static void close() {
        runtime.close();
    }

    @Test
    void bundleVersionsMatchTheVersionCatalog() {
        final var versions = runtime.versions();

        assertEquals(System.getProperty("jaspring.versions.angular"), versions.angular(), "rebuild tools/js-bundle");
        assertEquals(System.getProperty("jaspring.versions.postcss"), versions.postcss(), "rebuild tools/js-bundle");
    }

    @Test
    void parsesAnElementWithAngularsParser() {
        final var nodeName = runtime.parse("<div></div>", "test.html", ParseOptions.defaults(), result -> {
            final var nodes = result.getMember("nodes");
            assertEquals(1, nodes.getArraySize());
            assertEquals(0, result.getMember("errors").getArraySize());
            return nodes.getArrayElement(0).getMember("constructor").getMember("name").asString();
        });

        assertEquals("Element", nodeName);
    }

    @Test
    void reportsAngularsOwnErrorText() {
        final var text = runtime.parse("<div></span>", "test.html", ParseOptions.defaults(), result -> {
            final var errors = result.getMember("errors");
            assertEquals(1, errors.getArraySize());
            return errors.getArrayElement(0).getMember("text").asString();
        });

        assertTrue(text.contains("Unexpected closing tag \"span\""), text);
        assertTrue(text.contains("test.html@0:5"), text);
    }

    @Test
    void domSchemaKnowsStandardProperties() {
        final var hasInnerHtml = runtime.domSchema(schema ->
                schema.getMember("elements").getMember("div").hasMember("innerHTML")
                        || schema.getMember("elements").getMember("[HTMLElement]").hasMember("innerHTML"));

        assertTrue(hasInnerHtml);
    }

    @Test
    void scopesComponentCss() {
        final var css = runtime.processCss(":host { color: red } .a .b::before, :host(.x) > p:hover { top: 0 }", "jsp-c0");

        assertTrue(css.contains("[_nghost-jsp-c0] { color: red }"), css);
        assertTrue(css.contains(".a[_ngcontent-jsp-c0] .b[_ngcontent-jsp-c0]::before"), css);
        assertTrue(css.contains("[_nghost-jsp-c0].x > p[_ngcontent-jsp-c0]:hover"), css);
    }
}
