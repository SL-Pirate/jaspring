package dev.isira.jaspring.boot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import dev.isira.jaspring.boot.testapp.TestApplication;

@SpringBootTest(classes = TestApplication.class, properties = {"jaspring.tailwind.stylesheet=classpath:tailwind-test.css", "jaspring.live.transport=off"})
@AutoConfigureMockMvc
class TailwindTest {

    @Autowired MockMvc mockMvc;

    @Test
    void emitsUtilitiesForTheTemplatesClassNames() throws Exception {
        final var html = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("<style data-jsp-tailwind>"), html);
        assertTrue(html.contains(".p-4"), html);
        assertTrue(html.contains(".text-brand"), html);
        assertTrue(html.contains("--color-brand: #123456"), html);
        assertFalse(html.contains(".m-4"), "unused utilities stay out");
        assertTrue(html.contains("<h1 class=\"p-4 text-brand\""), html);
    }
}
