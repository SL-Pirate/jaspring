package dev.isira.jaspring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.isira.jaspring.live.LivePageRegistry;

@SpringBootTest(classes = TestApplication.class, properties = "jaspring.live.transport=off")
@AutoConfigureMockMvc
class OffTransportTest {

    @Autowired MockMvc mockMvc;
    @Autowired LivePageRegistry pages;

    @Test
    void rendersPlainHtmlWithoutShimOrPages() throws Exception {
        final var html = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(">Hello, Isira</h1>"), html);
        assertFalse(html.contains("data-jsp-on"), html);
        assertFalse(html.contains("jaspring.js"), html);
        assertFalse(html.contains("jaspring-wasm.js"), html);
        assertFalse(html.contains("rel=\"preload\""), html);
        assertEquals(0, pages.size());
    }
}
