package dev.isira.jaspring.sample.components;

import java.time.ZonedDateTime;
import java.util.Map;

import dev.isira.jaspring.component.Component;

@Component(selector = "app-pipes", templateUrl = "pipes.component.html")
public class PipesComponent {

    public final ZonedDateTime now = ZonedDateTime.now();
    public final double price = 1234.5678;
    public final Map<String, Object> payload = Map.of("framework", "jaspring", "version", 0.1);
}
