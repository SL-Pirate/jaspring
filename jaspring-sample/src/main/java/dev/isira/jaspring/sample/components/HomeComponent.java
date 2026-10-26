package dev.isira.jaspring.sample.components;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Input;

@Component(selector = "app-root", templateUrl = "home.component.html", styles = """
        :host { @apply mx-auto my-8 block max-w-3xl px-4 font-sans text-gray-900; }
        """)
public class HomeComponent {

    @Input public String title = "Jaspring";
}
