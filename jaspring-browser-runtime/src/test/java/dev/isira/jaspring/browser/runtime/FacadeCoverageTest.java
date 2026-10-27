package dev.isira.jaspring.browser.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.browser.BrowserPromise;

/// The runtime only executes under Web Image; on the JVM this checks that every facade has an implementation
/// and that the promise composition used by the dispatcher behaves.
class FacadeCoverageTest {

    @Test
    void everyFacadeIsImplemented() {
        final var client = WebClient.forHost("1");
        assertNotNull(client.localStorage());
        assertNotNull(client.sessionStorage());
        assertNotNull(client.cookies());
        assertNotNull(client.location());
        assertNotNull(client.geolocation());
        assertNotNull(client.clipboard());
        assertNotNull(client.notifications());
        assertNotNull(client.vibration());
    }

    @Test
    void resolvedPromisesMapEagerly() {
        assertEquals(BrowserPromise.of(21).then(v -> v * 2), BrowserPromise.of(42));
    }
}
