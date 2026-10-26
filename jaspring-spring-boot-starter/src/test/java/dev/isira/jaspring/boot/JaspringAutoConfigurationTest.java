package dev.isira.jaspring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import dev.isira.jaspring.boot.live.JaspringEventHandler;
import dev.isira.jaspring.boot.live.LiveEventController;
import dev.isira.jaspring.boot.testapp.components.CounterComponent;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.live.LivePageRegistry;

class JaspringAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JaspringAutoConfiguration.class))
            .withPropertyValues("jaspring.base-packages=dev.isira.jaspring.boot.testapp");

    @Test
    void defaultsToWebSocketWithHttpFallback() {
        runner.run(context -> {
            assertTrue(context.getBean(ComponentRegistry.class).components().stream().anyMatch(d -> d.type() == CounterComponent.class));
            assertTrue(context.containsBean("jaspringEventHandler"));
            assertTrue(context.getBeansOfType(LiveEventController.class).size() == 1);
            assertTrue(context.getBeansOfType(LivePageRegistry.class).size() == 1);
            assertEquals(JaspringProperties.Transport.WEBSOCKET, context.getBean(JaspringProperties.class).getLive().getTransport());
        });
    }

    @Test
    void httpTransportHasNoWebSocketBeans() {
        runner.withPropertyValues("jaspring.live.transport=http").run(context -> {
            assertFalse(context.getBeansOfType(JaspringEventHandler.class).size() > 0);
            assertEquals(1, context.getBeansOfType(LiveEventController.class).size());
        });
    }

    @Test
    void offTransportHasNoLiveEndpoints() {
        runner.withPropertyValues("jaspring.live.transport=off", "jaspring.change-detection=zoneless").run(context -> {
            assertEquals(0, context.getBeansOfType(JaspringEventHandler.class).size());
            assertEquals(0, context.getBeansOfType(LiveEventController.class).size());
            assertEquals(dev.isira.jaspring.zone.Zone.Mode.ZONELESS, context.getBean(JaspringProperties.class).getChangeDetection());
        });
    }
}
