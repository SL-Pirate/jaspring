package dev.isira.jaspring.boot.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import dev.isira.jaspring.boot.testapp.TestApplication;
import dev.isira.jaspring.boot.testapp.components.AsyncComponent;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jaspring.stability-timeout=1ms")
class WebSocketTransportTest {

    @LocalServerPort int port;

    @Test
    void joinEventAndPushOverOneSocket() throws Exception {
        AsyncComponent.delayMillis = 300;
        final var cookies = new CookieManager();
        final var http = HttpClient.newBuilder().cookieHandler(cookies).build();
        final var page = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<em>loading</em>"), "initial response does not wait longer than the stability timeout");
        final var matcher = Pattern.compile("data-jsp-page=\"([A-Za-z0-9_-]+)\"").matcher(page.body());
        assertTrue(matcher.find());
        final var pageId = matcher.group(1);
        final var sessionCookie = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow();

        final var received = new LinkedBlockingQueue<String>();
        final var socket = http.newWebSocketBuilder()
                .header("Cookie", "JSESSIONID=" + sessionCookie.getValue())
                .buildAsync(URI.create("ws://localhost:" + port + "/_jaspring/ws"), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            received.add(buffer.toString());
                            buffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);

        socket.sendText("{\"t\":\"join\",\"page\":\"" + pageId + "\"}", true).get(5, TimeUnit.SECONDS);
        final var pushed = received.poll(5, TimeUnit.SECONDS);
        assertTrue(pushed != null && pushed.contains("<li>ann</li><li>bob</li>"), "forked completion was pushed: " + pushed);

        socket.sendText("{\"t\":\"event\",\"page\":\"" + pageId + "\",\"h\":\"2.1\",\"ev\":{\"type\":\"click\"}}", true).get(5, TimeUnit.SECONDS);
        final var patch = received.poll(5, TimeUnit.SECONDS);
        assertTrue(patch != null && patch.contains("\"t\":\"patch\"") && patch.contains(">1</span>"), String.valueOf(patch));

        socket.sendText("{\"t\":\"event\",\"page\":\"" + pageId + "\",\"h\":\"4.1\",\"ev\":{\"type\":\"click\"}}", true).get(5, TimeUnit.SECONDS);
        final var call = received.poll(5, TimeUnit.SECONDS);
        assertTrue(call != null && call.contains("\"t\":\"call\"") && call.contains("\"target\":\"viewport\""), String.valueOf(call));
        final var idMatcher = Pattern.compile("\"id\":\"(\\d+)\"").matcher(call);
        assertTrue(idMatcher.find());
        socket.sendText("{\"t\":\"result\",\"page\":\"" + pageId + "\",\"id\":\"" + idMatcher.group(1) + "\",\"value\":{\"width\":640}}", true)
                .get(5, TimeUnit.SECONDS);
        final var applied = received.poll(5, TimeUnit.SECONDS);
        assertTrue(applied != null && applied.contains("\"t\":\"patch\"") && applied.contains("{width=640}"), String.valueOf(applied));

        socket.sendText("{\"t\":\"event\",\"page\":\"nope\",\"h\":\"2.1\",\"ev\":{}}", true).get(5, TimeUnit.SECONDS);
        final var error = received.poll(5, TimeUnit.SECONDS);
        assertTrue(error != null && error.contains("unknown-page"), String.valueOf(error));
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }
}
