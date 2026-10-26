package dev.isira.jaspring.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import dev.isira.jaspring.di.fixture.HelloComponent;
import dev.isira.jaspring.di.fixture.UpperPipe;

class SpringDiTest {

    @Configuration
    @ComponentScan("dev.isira.jaspring.di.fixture")
    static class Config {}

    private static AnnotationConfigApplicationContext context() {
        final var context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerScope(PageScope.NAME, new PageScope());
        context.register(Config.class);
        context.refresh();
        return context;
    }

    @Test
    void componentsAreConstructorInjectedPerCreationAndDestroyedOnDemand() {
        try (var context = context()) {
            final var factory = new SpringComponentFactory(context.getAutowireCapableBeanFactory());
            final var pageA = new PageScope.Beans("a");
            final var pageB = new PageScope.Beans("b");

            final var first = PageScope.runWith(pageA, () -> factory.create(HelloComponent.class));
            final var second = PageScope.runWith(pageA, () -> factory.create(HelloComponent.class));
            final var other = PageScope.runWith(pageB, () -> factory.create(HelloComponent.class));

            assertNotSame(first, second);
            assertSame(first.service, second.service);
            assertSame(first.counter, second.counter);
            assertNotSame(first.counter, other.counter);
            assertEquals("hello page 1", first.greeting());
            assertEquals("hello page 2", second.greeting());
            assertEquals("hello page 1", other.greeting());

            final var before = HelloComponent.destroyed;
            factory.destroy(first);
            assertEquals(before + 1, HelloComponent.destroyed);
        }
    }

    @Test
    void pageScopedBeansNeedAnActivePage() {
        try (var context = context()) {
            final var factory = new SpringComponentFactory(context.getAutowireCapableBeanFactory());
            assertThrows(RuntimeException.class, () -> factory.create(HelloComponent.class));
        }
    }

    @Test
    void scannerFindsComponentsAndPipes() {
        final var found = new ComponentScanner(getClass().getClassLoader()).scan(List.of("dev.isira.jaspring.di.fixture"));
        assertEquals(List.of(HelloComponent.class), found.components());
        assertEquals(List.of(UpperPipe.class), found.pipes());
    }
}
