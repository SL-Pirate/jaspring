package dev.isira.jaspring.sample;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "jaspring.stability-timeout=50ms")
@AutoConfigureMockMvc
class JaspringApplicationTests {

    @Autowired MockMvc mockMvc;

    @Test
    void homePageRendersEveryDemo() throws Exception {
        final var html = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(">Jaspring sample</h1>"), html);
        assertTrue(html.contains("<app-counter"), html);
        assertTrue(html.contains("Todos (1 left)"), html);
        assertTrue(html.contains("Loading users"), html);
        assertTrue(html.contains("currency: €1,234.57"), html);
        assertTrue(html.contains("data-jsp-page="), html);
    }
}
