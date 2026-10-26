package dev.isira.jaspring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import dev.isira.jaspring.boot.testapp.TestApplication;
import dev.isira.jaspring.live.LivePageRegistry;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class JaspringViewMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired LivePageRegistry pages;

    @Test
    void rendersTheDocumentWithStylesMarkersAndShim() throws Exception {
        final var result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        final var html = result.getResponse().getContentAsString();

        assertTrue(html.contains("<h1>Hello, Isira</h1>"), html);
        assertTrue(html.contains("<app-root data-jsp-v=\"1\">"), html);
        assertTrue(html.contains("<app-counter _nghost-jsp-c"), html);
        assertTrue(html.contains("data-jsp-on=\"click:2.1\""), html);
        assertTrue(html.contains("<li>ann</li><li>bob</li>"), "forked load settled before the response: " + html);
        assertTrue(html.contains("<style data-jsp-styles>"), html);
        assertTrue(html.contains("span[_ngcontent-jsp-c"), html);
        assertTrue(Pattern.compile("data-jsp-page=\"[A-Za-z0-9_-]{22}\" data-jsp-transport=\"websocket\"").matcher(html).find(), html);
        assertEquals("text/html;charset=UTF-8", result.getResponse().getContentType());

        mockMvc.perform(get("/_jaspring/jaspring.js")).andExpect(status().isOk());
    }

    @Test
    void httpTransportRunsEventsForTheOwningSessionOnly() throws Exception {
        final var first = mockMvc.perform(get("/")).andReturn();
        final var session = (MockHttpSession) first.getRequest().getSession();
        final var html = first.getResponse().getContentAsString();
        final var matcher = Pattern.compile("data-jsp-page=\"([A-Za-z0-9_-]+)\"").matcher(html);
        assertTrue(matcher.find());
        final var pageId = matcher.group(1);

        final var body = "{\"t\":\"event\",\"page\":\"" + pageId + "\",\"h\":\"2.1\",\"ev\":{\"type\":\"click\"}}";
        final var response = mockMvc.perform(post("/_jaspring/event").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("\"t\":\"patch\""), response);
        assertTrue(response.contains("<span _ngcontent-jsp-c"), response);
        assertTrue(response.contains(">1</span>"), response);
        assertTrue(response.contains("\"pending\":false"), response);

        mockMvc.perform(post("/_jaspring/event").session(new MockHttpSession()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isGone());
        mockMvc.perform(get("/_jaspring/poll").session(session).param("page", pageId)).andExpect(status().isOk());
    }
}
