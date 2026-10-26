package dev.isira.jaspring.sample.components.brew;

import dev.isira.jaspring.component.Component;

import java.util.List;

@Component(selector = "brew-card", templateUrl = "./brew-card.component.html", styleUrl = "./brew-card.component.css")
public class BrewCardComponent {
    public final double circumference = 2 * Math.PI * 88.0;

    public final List<BrewStep> steps = List.of(
            new BrewStep("Bloom", "30g", "30s", "Pour 30g of water and wait for 30 seconds."),
            new BrewStep("Pour", "200g", "1m 30s", "Pour 200g of water in a circular motion."),
            new BrewStep("Drawdown", "400g", "2m 30s", "Wait for the water to draw down.")
    );

    public int active = 0;

    public void select(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new IllegalArgumentException("Invalid step index: " + index);
        }
        this.active = index;
    }

    public void next() {
        active = (active + 1) % steps.size();
    }

    public double getProgressOffset() {
        final var fraction = (double) (active + 1) / steps.size();
        return circumference * (1 - fraction);
    }
}
