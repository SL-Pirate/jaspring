package dev.isira.jaspring.di.fixture;

import jakarta.annotation.PreDestroy;

import dev.isira.jaspring.component.Component;

@Component(selector = "app-hello", template = "{{ greeting }}")
public class HelloComponent {

    public final GreetingService service;
    public final PageCounter counter;
    public static int destroyed;

    public HelloComponent(GreetingService service, PageCounter counter) {
        this.service = service;
        this.counter = counter;
    }

    public String greeting() {
        return service.greet("page " + counter.next());
    }

    @PreDestroy
    void shutdown() {
        destroyed++;
    }
}
