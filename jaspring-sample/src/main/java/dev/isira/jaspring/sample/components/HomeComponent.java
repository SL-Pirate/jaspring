package dev.isira.jaspring.sample.components;

import dev.isira.jaspring.component.Component;
import dev.isira.jaspring.component.Input;

@Component(selector = "app-root", templateUrl = "home.component.html", styles = """
        :host { @apply mx-auto my-8 block max-w-3xl px-4 font-sans text-gray-900; }
        section { @apply mb-8 rounded-xl border border-gray-200 bg-white p-5 shadow-sm; }
        h2 { @apply mb-3 text-lg font-semibold text-brand; }
        .hint { @apply text-sm text-gray-500; }
        """)
public class HomeComponent {

    @Input public String title = "Jaspring";
}
