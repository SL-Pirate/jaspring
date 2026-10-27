package dev.isira.jaspring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertTrue(html.contains(">Hello, Isira</h1>"), html);
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

    @Test
    void httpTransportDeliversCallsAndAppliesResults() throws Exception {
        final var first = mockMvc.perform(get("/")).andReturn();
        final var session = (MockHttpSession) first.getRequest().getSession();
        final var pageId = pageId(first.getResponse().getContentAsString());

        final var click = "{\"t\":\"event\",\"page\":\"" + pageId + "\",\"h\":\"4.1\",\"ev\":{\"type\":\"click\"}}";
        final var reply = mockMvc.perform(post("/_jaspring/event").session(session).contentType(MediaType.APPLICATION_JSON).content(click))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(reply.contains("\"t\":\"call\""), reply);
        assertTrue(reply.contains("\"target\":\"viewport\""), reply);
        assertTrue(reply.contains("\"pending\":true"), "unstable until the browser answers: " + reply);
        final var idMatcher = Pattern.compile("\"t\":\"call\",\"id\":\"(\\d+)\"").matcher(reply);
        assertTrue(idMatcher.find(), reply);

        final var result = "{\"t\":\"result\",\"page\":\"" + pageId + "\",\"id\":\"" + idMatcher.group(1) + "\",\"value\":{\"width\":800}}";
        final var patched = mockMvc.perform(post("/_jaspring/event").session(session).contentType(MediaType.APPLICATION_JSON).content(result))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(patched.contains("\"t\":\"patch\""), patched);
        assertTrue(patched.contains("{width=800}"), patched);
        assertTrue(patched.contains("\"pending\":false"), patched);

        final var bogus = mockMvc.perform(post("/_jaspring/event").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"t\":\"bogus\",\"page\":\"" + pageId + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(bogus.contains("\"code\":\"protocol\""), bogus);
    }

    @Test
    void callsMadeWhileRenderingTravelWithTheDocumentAndTheInitialPoll() throws Exception {
        final var started = System.nanoTime();
        final var first = mockMvc.perform(get("/browser")).andExpect(status().isOk()).andReturn();
        final var elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        final var session = (MockHttpSession) first.getRequest().getSession();
        final var html = first.getResponse().getContentAsString();
        final var pageId = pageId(html);

        assertTrue(elapsedMillis < 5_000, "the render must not wait for the browser: " + elapsedMillis + "ms");
        assertTrue(html.contains("<b>?</b>"), html);
        assertTrue(html.contains("data-jsp-pending=\"true\""), html);

        final var polled = mockMvc.perform(get("/_jaspring/poll").session(session).param("page", pageId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(polled.contains("\"t\":\"call\""), polled);
        assertTrue(polled.contains("\"target\":\"viewport\""), polled);
        assertTrue(polled.contains("\"pending\":true"), polled);
    }

    @Test
    void pagesLoadTheWasmRuntimeAccordingToTheirClosures() throws Exception {
        // The test manifest lists an event-time closure in BrowserComponent and a mount-time one in BrowserPageComponent.
        final var home = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertTrue(home.contains("data-jsp-on=\"click:4.1:w\""), "handlers that run closures carry the w flag: " + home);
        assertTrue(home.contains("data-jsp-wasm-policy=\"idle\""), home);
        assertFalse(home.contains("rel=\"preload\""), home);

        final var browser = mockMvc.perform(get("/browser")).andReturn().getResponse().getContentAsString();
        assertTrue(browser.contains("data-jsp-wasm-policy=\"eager\""), browser);
        assertTrue(browser.contains("<link rel=\"preload\" as=\"script\" href=\"/_jaspring/app/"), browser);
        assertTrue(browser.contains("<link rel=\"preload\" as=\"fetch\" crossorigin href=\"/_jaspring/app/"), browser);
    }

    @Test
    void hashedAssetsAreServedImmutablyAndStaleDigestsMiss() throws Exception {
        final var html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        final var matcher = Pattern.compile("data-jsp-wasm-js=\"(/_jaspring/app/([0-9a-f]{16})/jaspring-app\\.js)\"").matcher(html);
        assertTrue(matcher.find(), html);
        final var launcher = matcher.group(1);
        final var digest = matcher.group(2);

        final var js = mockMvc.perform(get(launcher)).andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(js.getHeader("Cache-Control").contains("immutable"), js.getHeader("Cache-Control"));
        assertTrue(js.getContentAsString().contains("JaspringWasm"));
        final var wasm = mockMvc.perform(get(launcher + ".wasm")).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("application/wasm", wasm.getContentType());

        mockMvc.perform(get("/_jaspring/app/0000000000000000/jaspring-app.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/_jaspring/app/" + digest + "/other.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/_jaspring/jaspring-wasm.js")).andExpect(status().isOk());
    }

    private static String pageId(String html) {
        final var matcher = Pattern.compile("data-jsp-page=\"([A-Za-z0-9_-]+)\"").matcher(html);
        assertTrue(matcher.find(), html);
        return matcher.group(1);
    }
}
