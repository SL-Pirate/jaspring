package dev.isira.jaspring.template.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import tools.jackson.databind.json.JsonMapper;

/// Replays Angular's own `r3_template_transform_spec` expectations through the bundled compiler and the Java converter.
class R3TemplateTransformCompatTest {

    record Case(String name, String html, boolean ignoreError, List<List<Object>> expected) {}

    @TestFactory
    List<DynamicTest> angularTemplateTransformCases() {
        return cases().stream()
                .map(c -> DynamicTest.dynamicTest(c.name() + " :: " + c.html(), () -> {
                    final var parsed = ParserFixture.PARSER.parse(c.html(), "spec.html", new ParseOptions(false, c.ignoreError()));
                    assertEquals(normalize(c.expected()), normalize(Humanizer.humanize(parsed.nodes())), c.html());
                }))
                .toList();
    }

    private static List<Case> cases() {
        try (var in = R3TemplateTransformCompatTest.class.getResourceAsStream("/jaspring/compat/r3-template-transform.json")) {
            return JsonMapper.builder().build().readValue(in, JsonMapper.builder().build().getTypeFactory()
                    .constructCollectionType(List.class, Case.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Jackson reads enum ordinals as Integer and Angular's delays as numbers; compare everything as strings.
    private static List<List<String>> normalize(List<List<Object>> rows) {
        return rows.stream().map(row -> row.stream().map(v -> v == null ? null : String.valueOf(v)).toList()).toList();
    }
}
