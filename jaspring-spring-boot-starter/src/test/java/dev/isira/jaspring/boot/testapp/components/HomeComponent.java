package dev.isira.jaspring.boot.testapp.components;

import dev.isira.jaspring.boot.testapp.services.Greeter;
import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Input;

@Component(selector = "app-root", template = "<h1 class=\"p-4 text-brand\">{{ greeter.greet(title) }}</h1><app-counter></app-counter><app-async></app-async>")
public class HomeComponent {

    @Input public String title = "world";
    public final Greeter greeter;

    public HomeComponent(Greeter greeter) {
        this.greeter = greeter;
    }
}
