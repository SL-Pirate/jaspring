package dev.isira.jaspring.sample.components;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Input;

@Component(selector = "app-root", templateUrl = "home.component.html", styles = """
        :host { font-family: system-ui, sans-serif; max-width: 48rem; margin: 2rem auto; display: block; }
        section { margin-bottom: 2rem; }
        .hint { color: #888; }
        """)
public class HomeComponent {

    @Input public String title = "Jaspring";
}
