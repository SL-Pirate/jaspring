package dev.isira.jaspring.sample.controllers;

import dev.isira.jaspring.boot.JaspringView;
import dev.isira.jaspring.sample.components.HomeComponent;
import dev.isira.jaspring.sample.components.brew.BrewCardComponent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public JaspringView home() {
        return JaspringView.of(HomeComponent.class).input("title", "Jaspring sample");
    }

    @GetMapping("/brew")
    public JaspringView brew() {
        return JaspringView.of(BrewCardComponent.class);
    }
}
